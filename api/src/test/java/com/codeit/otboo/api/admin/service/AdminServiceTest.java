package com.codeit.otboo.api.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.api.admin.dto.UserLockUpdateRequest;
import com.codeit.otboo.api.admin.dto.UserRoleUpdateRequest;
import com.codeit.otboo.api.user.dto.UserDto;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.RefreshTokenRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);
    private final AdminService adminService =
            new AdminService(userRepository, refreshTokenRepository);

    private User user(UserRole role, boolean locked, int tokenVersion) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("woody@otboo.io")
                .name("우디")
                .password("encoded")
                .role(role)
                .locked(locked)
                .tokenVersion(tokenVersion)
                .build();
    }

    /** 권한 변경 시 tokenVersion 이 증가해 기존 토큰이 무효화되는지 확인합니다. */
    @Test
    void invalidatesSessionWhenRoleChanged() {
        UUID userId = UUID.randomUUID();
        User target = user(UserRole.USER, false, 2);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserDto result = adminService.updateRole(
                userId, new UserRoleUpdateRequest(UserRole.ADMIN));

        assertThat(result.role()).isEqualTo("ADMIN");
        assertThat(target.getTokenVersion()).isEqualTo(3);
        verify(refreshTokenRepository).deleteByUser(target);
    }

    /** 계정 잠금 시 tokenVersion 이 증가해 즉시 로그아웃되는지 확인합니다. */
    @Test
    void invalidatesSessionWhenAccountLocked() {
        UUID userId = UUID.randomUUID();
        User target = user(UserRole.USER, false, 1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserDto result = adminService.updateLock(userId, new UserLockUpdateRequest(true));

        assertThat(result.locked()).isTrue();
        assertThat(target.getTokenVersion()).isEqualTo(2);
        verify(refreshTokenRepository).deleteByUser(target);
    }

    /** 잠금 해제 시에는 기존 세션을 무효화하지 않는지 확인합니다. */
    @Test
    void keepsSessionWhenAccountUnlocked() {
        UUID userId = UUID.randomUUID();
        User target = user(UserRole.USER, true, 5);
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        UserDto result = adminService.updateLock(userId, new UserLockUpdateRequest(false));

        assertThat(result.locked()).isFalse();
        assertThat(target.getTokenVersion()).isEqualTo(5);
        verify(refreshTokenRepository, never()).deleteByUser(target);
    }

    /** 존재하지 않는 사용자의 권한 변경은 거부하는지 확인합니다. */
    @Test
    void rejectsRoleChangeForUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateRole(
                userId, new UserRoleUpdateRequest(UserRole.ADMIN)))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    /** 존재하지 않는 사용자의 잠금 변경은 거부하는지 확인합니다. */
    @Test
    void rejectsLockChangeForUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateLock(
                userId, new UserLockUpdateRequest(true)))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }
}
