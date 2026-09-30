package com.codeit.otboo.api.auth.dto;

import com.codeit.otboo.domain.user.entity.OAuthProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public record UserOauthInfo(
        OAuthProvider provider,
        String providerId,
        String nickname,
        String email,
        String profileImageUrl
) {
    public static UserOauthInfo of(
            OAuthProvider provider,
            OidcUser oidcUser
    ) {
        return switch (provider) {
            case GOOGLE -> ofGoogle(oidcUser);
            case KAKAO -> ofKakao(oidcUser);
        };
    }

    private static UserOauthInfo ofGoogle(OidcUser user) {
        return new UserOauthInfo(
                OAuthProvider.GOOGLE,
                user.getSubject(),
                firstNonBlank(user.getFullName(), user.getClaimAsString("name")),
                user.getEmail(),
                user.getPicture()
        );
    }

    private static UserOauthInfo ofKakao(OidcUser user) {
        return new UserOauthInfo(
                OAuthProvider.KAKAO,
                user.getSubject(),
                firstNonBlank(user.getClaimAsString("nickname"), user.getFullName(),
                        user.getClaimAsString("name")),
                user.getClaimAsString("email"),
                user.getClaimAsString("picture")
        );
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
