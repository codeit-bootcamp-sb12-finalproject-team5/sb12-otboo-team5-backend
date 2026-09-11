package com.codeit.otboo.domain.notification.entity;

public enum NotificationType {
    WEATHER_RAIN,
    FEED_CREATED,
    ROLE_CHANGED,
    FEED_LIKED,
    FEED_COMMENTED,
    FOLLOWED,
    DM_RECEIVED,
    // V3 이전 알림의 원래 유형을 알 수 없는 경우에만 사용한다.
    LEGACY
}
