package com.codeit.otboo.api.follow.dto.response;

import java.util.UUID;

public record FollowUserDto(
    UUID userId,
    String name,
    String profileImageUrl
) {

}
