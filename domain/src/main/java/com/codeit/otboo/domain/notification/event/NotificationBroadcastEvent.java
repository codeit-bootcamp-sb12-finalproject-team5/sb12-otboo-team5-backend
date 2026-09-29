package com.codeit.otboo.domain.notification.event;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import java.util.List;

public record NotificationBroadcastEvent(
        int schemaVersion,
        NotificationDto notification,
        List<NotificationDeliveryRef> notifications
) {
    public NotificationBroadcastEvent(int schemaVersion, NotificationDto notification) {
        this(schemaVersion, notification, null);
    }

    public static NotificationBroadcastEvent of(NotificationDto notification) {
        return of(List.of(notification));
    }

    public static NotificationBroadcastEvent of(List<NotificationDto> notifications) {
        return new NotificationBroadcastEvent(2, null, notifications.stream()
                .map(value -> new NotificationDeliveryRef(value.id(), value.receiverId()))
                .toList());
    }
}
