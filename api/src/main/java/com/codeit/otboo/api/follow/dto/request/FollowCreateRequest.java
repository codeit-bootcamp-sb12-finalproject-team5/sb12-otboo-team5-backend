package com.codeit.otboo.api.follow.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record FollowCreateRequest(
    @NotNull
    UUID followeeId,
    @NotNull
    UUID followerId
) {

}
