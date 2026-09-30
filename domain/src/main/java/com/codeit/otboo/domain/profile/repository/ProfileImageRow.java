package com.codeit.otboo.domain.profile.repository;

import java.util.UUID;

/** JPQL alias 결과를 프로필 이미지 조회 projection으로 변환하기 위한 내부 인터페이스다. */
public interface ProfileImageRow {

    UUID getUserId();

    String getProfileImageUrl();
}
