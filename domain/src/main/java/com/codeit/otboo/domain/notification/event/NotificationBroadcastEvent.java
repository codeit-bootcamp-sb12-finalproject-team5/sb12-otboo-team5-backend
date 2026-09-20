package com.codeit.otboo.domain.notification.event;

import com.codeit.otboo.domain.notification.dto.NotificationDto;

public record NotificationBroadcastEvent(
    int schemaVersion,
    NotificationDto notification
) {
    public static NotificationBroadcastEvent of(NotificationDto notification) {
        return new NotificationBroadcastEvent(1, notification);
    }
}
