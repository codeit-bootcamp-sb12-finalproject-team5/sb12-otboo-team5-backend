package com.codeit.otboo.domain.notification.event;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import java.util.UUID;

public record SingleNotificationCreateEvent(
        UUID receiverId,
        String title,
        String content,
        NotificationLevel level
) {
}
