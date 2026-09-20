package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

public record FollowNotificationPayload(
    UUID followId
) {
}
