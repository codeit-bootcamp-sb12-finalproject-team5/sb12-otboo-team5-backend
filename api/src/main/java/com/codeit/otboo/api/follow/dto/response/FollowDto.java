package com.codeit.otboo.api.follow.dto.response;

import java.util.UUID;

public record FollowDto(
    UUID id,
    FollowUserDto followee,
    FollowUserDto follower
) {

}
