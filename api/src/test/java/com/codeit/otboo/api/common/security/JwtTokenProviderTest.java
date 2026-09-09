package com.codeit.otboo.api.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final JwtTokenProvider provider =
            new JwtTokenProvider(new JwtProperties(SECRET, 1800, 1209600));

    /** Access Token 에 사용자 정보와 tokenVersion 이 담기는지 확인합니다. */
    @Test
    void createsAccessTokenWithUserClaims() {
        UUID userId = UUID.randomUUID();

        String token = provider.createAccessToken(userId, "woody@otboo.io", "USER", 3);
        Claims claims = provider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("woody@otboo.io");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("tokenVersion", Integer.class)).isEqualTo(3);
    }

    /** Refresh Token 에도 tokenVersion 이 담기는지 확인합니다. */
    @Test
    void createsRefreshTokenWithTokenVersion() {
        UUID userId = UUID.randomUUID();

        String token = provider.createRefreshToken(userId, 5);
        Claims claims = provider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("tokenVersion", Integer.class)).isEqualTo(5);
    }

    /** 정상 발급된 토큰은 검증을 통과하는지 확인합니다. */
    @Test
    void validatesGeneratedToken() {
        String token = provider.createAccessToken(UUID.randomUUID(), "a@b.io", "USER", 0);

        assertThat(provider.validate(token)).isTrue();
    }

    /** 위조되거나 형식이 잘못된 토큰은 거부하는지 확인합니다. */
    @Test
    void rejectsTamperedToken() {
        String token = provider.createAccessToken(UUID.randomUUID(), "a@b.io", "USER", 0);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(provider.validate(tampered)).isFalse();
        assertThat(provider.validate("not-a-token")).isFalse();
    }

    /** 만료된 토큰은 거부하는지 확인합니다. */
    @Test
    void rejectsExpiredToken() {
        JwtTokenProvider expiredProvider =
                new JwtTokenProvider(new JwtProperties(SECRET, -1, -1));
        String token = expiredProvider.createAccessToken(
                UUID.randomUUID(), "a@b.io", "USER", 0);

        assertThat(expiredProvider.validate(token)).isFalse();
    }

    /** 다른 시크릿으로 발급된 토큰은 거부하는지 확인합니다. */
    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                1800, 1209600));
        String token = other.createAccessToken(UUID.randomUUID(), "a@b.io", "USER", 0);

        assertThat(provider.validate(token)).isFalse();
    }
}
