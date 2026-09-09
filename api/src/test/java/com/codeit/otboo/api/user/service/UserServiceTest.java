package com.codeit.otboo.api.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.api.user.dto.ChangePasswordRequest;
import com.codeit.otboo.api.user.dto.UserCreateRequest;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService = new UserService(userRepository, passwordEncoder);

    /** 회원가입 시 비밀번호를 암호화하고 USER 권한으로 저장하는지 확인합니다. */
    @Test
    void createsUserWithEncodedPasswordAndDefaultRole() {
        UserCreateRequest request =
                new UserCreateRequest("우디", "woody@otboo.io", "otboo1234");
        when(userRepository.existsByEmail("woody@otboo.io")).thenReturn(false);
        when(passwordEncoder.encode("otboo1234")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserDto result = userService.create(request);

        assertThat(result.email()).isEqualTo("woody@otboo.io");
        assertThat(result.name()).isEqualTo("우디");
        assertThat(result.role()).isEqualTo("USER");
        assertThat(result.locked()).isFalse();
        verify(passwordEncoder).encode("otboo1234");
    }

    /** 이미 사용 중인 이메일이면 저장하지 않고 예외를 던지는지 확인합니다. */
    @Test
    void rejectsDuplicateEmail() {
        UserCreateRequest request =
                new UserCreateRequest("우디", "woody@otboo.io", "otboo1234");
        when(userRepository.existsByEmail("woody@otboo.io")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));

        verify(userRepository, never()).save(any(User.class));
    }

    /** 비밀번호 변경 시 임시 비밀번호가 함께 파기되는지 확인합니다. */
    @Test
    void discardsTempPasswordWhenPasswordChanged() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("woody@otboo.io")
                .name("우디")
                .password("old")
                .role(UserRole.USER)
                .locked(false)
                .tokenVersion(0)
                .tempPassword("temp-encoded")
                .tempPasswordExpiresAt(OffsetDateTime.now().plusMinutes(3))
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpassword123")).thenReturn("new-encoded");

        userService.changePassword(userId, new ChangePasswordRequest("newpassword123"));

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        assertThat(user.getTempPassword()).isNull();
        assertThat(user.getTempPasswordExpiresAt()).isNull();
    }

    /** 존재하지 않는 사용자의 비밀번호 변경은 거부하는지 확인합니다. */
    @Test
    void rejectsPasswordChangeForUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                userService.changePassword(userId, new ChangePasswordRequest("newpassword123")))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
