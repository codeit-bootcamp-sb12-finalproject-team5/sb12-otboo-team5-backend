package com.codeit.otboo.api.user.dto;

import com.codeit.otboo.domain.user.entity.User;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        Instant createdAt,
        String email,
        String name,
        String role,
        boolean locked
) {

    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toInstant(),
                user.getEmail(),
                user.getName(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getLocked())
        );
    }
}
