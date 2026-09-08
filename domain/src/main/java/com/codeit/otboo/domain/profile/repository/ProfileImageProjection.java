package com.codeit.otboo.domain.profile.repository;

import java.util.UUID;

/** 상대방 프로필 이미지를 사용자 ID로 연결하기 위한 조회 전용 projection이다. */
public record ProfileImageProjection(
    UUID userId,
    String profileImageUrl
) {
    public static ProfileImageProjection from(ProfileImageRow row) {
        return new ProfileImageProjection(row.getUserId(), row.getProfileImageUrl());
    }
}
