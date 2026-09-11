package com.codeit.otboo.api.auth.dto;

import com.codeit.otboo.api.user.dto.UserDto;

public record JwtDto(
        UserDto userDto,
        String accessToken,
        String refreshToken
) {
}
