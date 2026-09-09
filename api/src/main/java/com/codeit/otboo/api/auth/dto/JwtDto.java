package com.codeit.otboo.api.auth.dto;

public record JwtDto(
        String accessToken,
        String refreshToken
) {
}
