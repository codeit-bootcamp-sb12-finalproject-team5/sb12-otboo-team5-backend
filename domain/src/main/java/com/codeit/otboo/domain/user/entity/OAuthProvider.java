package com.codeit.otboo.domain.user.entity;

public enum OAuthProvider {
    GOOGLE,
    KAKAO;

    public static OAuthProvider from(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> GOOGLE;
            case "kakao" -> KAKAO;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 OAuth Provider입니다: " + registrationId
            );
        };
    }
}
