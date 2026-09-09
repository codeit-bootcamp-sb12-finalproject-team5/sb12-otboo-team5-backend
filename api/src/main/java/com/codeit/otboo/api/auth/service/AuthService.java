package com.codeit.otboo.api.auth.service;

import com.codeit.otboo.api.auth.dto.JwtDto;
import com.codeit.otboo.api.auth.dto.SignInRequest;
import com.codeit.otboo.api.common.mail.EmailSender;
import com.codeit.otboo.api.common.mail.TempPasswordGenerator;
import com.codeit.otboo.api.common.security.JwtTokenProvider;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.RefreshToken;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.RefreshTokenRepository;
import com.codeit.otboo.domain.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TempPasswordGenerator tempPasswordGenerator;
    private final EmailSender emailSender;

    private static final int TEMP_PASSWORD_VALID_MINUTES = 3;

    /** 로그인 */
    @Transactional
    public JwtDto signIn(SignInRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(UserException::invalidCredentials);

        if (Boolean.TRUE.equals(user.getLocked())) {
            throw UserException.accountLocked();
        }

        if (!matchesPassword(user, request.password())) {
            throw UserException.invalidCredentials();
        }

        // 기존 세션 무효화 (동시 로그인 차단)
        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenRepository.deleteByUser(user);

        return issueTokens(user);
    }

    /** 비밀번호 초기화 - 임시 비밀번호 발급 */
    @Transactional
    public void resetPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String tempPassword = tempPasswordGenerator.generate();

            user.setTempPassword(passwordEncoder.encode(tempPassword));
            user.setTempPasswordExpiresAt(
                    OffsetDateTime.now().plusMinutes(TEMP_PASSWORD_VALID_MINUTES));

            emailSender.sendTempPassword(user.getEmail(), tempPassword);
            log.info("임시 비밀번호 발급: {}", user.getEmail());
        });
        // 계정이 없어도 동일하게 응답합니다 (계정 존재 여부 노출 방지)
    }

    /** 로그아웃 */
    @Transactional
    public void signOut(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserException::notFound);

        user.setTokenVersion(user.getTokenVersion() + 1);
        refreshTokenRepository.deleteByUser(user);

        log.info("로그아웃: {}", user.getEmail());
    }

    /** 토큰 재발급 */
    @Transactional
    public JwtDto refresh(String refreshToken) {
        if (refreshToken == null || !tokenProvider.validate(refreshToken)) {
            throw new UserException(ErrorCode.INVALID_TOKEN);
        }

        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new UserException(ErrorCode.INVALID_TOKEN));

        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new UserException(ErrorCode.EXPIRED_TOKEN);
        }

        Claims claims = tokenProvider.parseClaims(refreshToken);
        Integer version = claims.get("tokenVersion", Integer.class);

        User user = stored.getUser();
        if (!user.getTokenVersion().equals(version)) {
            throw new UserException(ErrorCode.TOKEN_VERSION_MISMATCH);
        }

        refreshTokenRepository.delete(stored);
        return issueTokens(user);
    }

    /** 비밀번호 또는 임시 비밀번호 검증 */
    private boolean matchesPassword(User user, String rawPassword) {
        if (passwordEncoder.matches(rawPassword, user.getPassword())) {
            return true;
        }

        String temp = user.getTempPassword();
        if (temp == null) {
            return false;
        }

        OffsetDateTime expiresAt = user.getTempPasswordExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(OffsetDateTime.now())) {
            throw new UserException(ErrorCode.TEMP_PASSWORD_EXPIRED);
        }

        return passwordEncoder.matches(rawPassword, temp);
    }

    private JwtDto issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(
                user.getId(), user.getEmail(), user.getRole().name(), user.getTokenVersion());

        String refreshToken = tokenProvider.createRefreshToken(
                user.getId(), user.getTokenVersion());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(OffsetDateTime.now()
                        .plusSeconds(tokenProvider.getRefreshValiditySeconds()))
                .build());

        log.info("토큰 발급: {}", user.getEmail());
        return new JwtDto(accessToken, refreshToken);
    }
}
