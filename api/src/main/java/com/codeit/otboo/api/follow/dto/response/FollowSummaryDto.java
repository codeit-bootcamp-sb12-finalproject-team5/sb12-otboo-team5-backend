package com.codeit.otboo.api.follow.dto.response;

import java.util.UUID;

public record FollowSummaryDto(
    UUID followeeId,
    long followerCount,
    long followingCount,
    boolean followedByMe,
    UUID followedByMeId,
    boolean followingMe
) {

}
