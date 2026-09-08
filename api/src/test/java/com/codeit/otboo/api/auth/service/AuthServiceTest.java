package com.codeit.otboo.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.api.auth.dto.JwtDto;
import com.codeit.otboo.api.auth.dto.SignInRequest;
import com.codeit.otboo.api.common.mail.EmailSender;
import com.codeit.otboo.api.common.mail.TempPasswordGenerator;
import com.codeit.otboo.api.common.security.JwtProperties;
import com.codeit.otboo.api.common.security.JwtTokenProvider;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.RefreshToken;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.RefreshTokenRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository =
            mock(RefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TempPasswordGenerator tempPasswordGenerator = mock(TempPasswordGenerator.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final JwtTokenProvider tokenProvider =
            new JwtTokenProvider(new JwtProperties(SECRET, 1800, 1209600));

    private final AuthService authService = new AuthService(
            userRepository, refreshTokenRepository, passwordEncoder,
            tokenProvider, tempPasswordGenerator, emailSender);

    private User user(String password, boolean locked, int tokenVersion) {
        return User.builder()
                .id(UUID.randomUUID())
                .email("woody@otboo.io")
                .name("우디")
                .password(password)
                .role(UserRole.USER)
                .locked(locked)
                .tokenVersion(tokenVersion)
                .build();
    }

    /** 로그인 성공 시 토큰을 발급하고 Refresh Token 을 저장하는지 확인합니다. */
    @Test
    void issuesTokensOnSuccessfulSignIn() {
        User found = user("encoded", false, 0);
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));
        when(passwordEncoder.matches("otboo1234", "encoded")).thenReturn(true);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        JwtDto result = authService.signIn(new SignInRequest("woody@otboo.io", "otboo1234"));

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    /** 로그인 시 tokenVersion 이 증가해 기존 세션이 무효화되는지 확인합니다. */
    @Test
    void increasesTokenVersionOnSignInToBlockConcurrentSession() {
        User found = user("encoded", false, 3);
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));
        when(passwordEncoder.matches("otboo1234", "encoded")).thenReturn(true);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        authService.signIn(new SignInRequest("woody@otboo.io", "otboo1234"));

        assertThat(found.getTokenVersion()).isEqualTo(4);
        verify(refreshTokenRepository).deleteByUser(found);
    }

    /** 존재하지 않는 이메일은 인증 실패로 처리하는지 확인합니다. */
    @Test
    void rejectsUnknownEmail() {
        when(userRepository.findByEmail("nobody@otboo.io")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.signIn(new SignInRequest("nobody@otboo.io", "otboo1234")))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    /** 비밀번호가 일치하지 않으면 인증 실패로 처리하는지 확인합니다. */
    @Test
    void rejectsWrongPassword() {
        User found = user("encoded", false, 0);
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() ->
                authService.signIn(new SignInRequest("woody@otboo.io", "wrong")))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
    }

    /** 잠긴 계정은 비밀번호 검증 전에 차단하는지 확인합니다. */
    @Test
    void rejectsLockedAccountBeforePasswordCheck() {
        User found = user("encoded", true, 0);
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));

        assertThatThrownBy(() ->
                authService.signIn(new SignInRequest("woody@otboo.io", "otboo1234")))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    /** 유효한 임시 비밀번호로도 로그인할 수 있는지 확인합니다. */
    @Test
    void allowsSignInWithValidTempPassword() {
        User found = user("encoded", false, 0);
        found.setTempPassword("temp-encoded");
        found.setTempPasswordExpiresAt(OffsetDateTime.now().plusMinutes(3));
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));
        when(passwordEncoder.matches("temp1234", "encoded")).thenReturn(false);
        when(passwordEncoder.matches("temp1234", "temp-encoded")).thenReturn(true);
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(i -> i.getArgument(0));

        JwtDto result = authService.signIn(new SignInRequest("woody@otboo.io", "temp1234"));

        assertThat(result.accessToken()).isNotBlank();
    }

    /** 만료된 임시 비밀번호는 거부하는지 확인합니다. */
    @Test
    void rejectsExpiredTempPassword() {
        User found = user("encoded", false, 0);
        found.setTempPassword("temp-encoded");
        found.setTempPasswordExpiresAt(OffsetDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));
        when(passwordEncoder.matches("temp1234", "encoded")).thenReturn(false);

        assertThatThrownBy(() ->
                authService.signIn(new SignInRequest("woody@otboo.io", "temp1234")))
                .isInstanceOfSatisfying(UserException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TEMP_PASSWORD_EXPIRED));
    }

    /** 비밀번호 초기화 시 임시 비밀번호를 저장하고 메일을 발송하는지 확인합니다. */
    @Test
    void issuesTempPasswordAndSendsEmail() {
        User found = user("encoded", false, 0);
        when(userRepository.findByEmail("woody@otboo.io")).thenReturn(Optional.of(found));
        when(tempPasswordGenerator.generate()).thenReturn("Temp12!@ab");
        when(passwordEncoder.encode("Temp12!@ab")).thenReturn("temp-encoded");

        authService.resetPassword("woody@otboo.io");

        assertThat(found.getTempPassword()).isEqualTo("temp-encoded");
        assertThat(found.getTempPasswordExpiresAt()).isAfter(OffsetDateTime.now());
        verify(emailSender).sendTempPassword("woody@otboo.io", "Temp12!@ab");
    }

    /** 존재하지 않는 계정이어도 예외 없이 종료해 계정 존재 여부를 노출하지 않는지 확인합니다. */
    @Test
    void doesNotRevealAccountExistenceOnPasswordReset() {
        when(userRepository.findByEmail("nobody@otboo.io")).thenReturn(Optional.empty());

        authService.resetPassword("nobody@otboo.io");

        verify(emailSender, never()).sendTempPassword(anyString(), anyString());
    }
}
