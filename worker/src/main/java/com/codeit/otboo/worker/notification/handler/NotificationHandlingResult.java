package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import java.util.List;

public record NotificationHandlingResult(
        List<NotificationDto> notifications,
        NotificationCreateMessage<?> continuation,
        String continuationKey
) {
    public NotificationHandlingResult {
        notifications = List.copyOf(notifications);
    }

    public static NotificationHandlingResult completed(List<NotificationDto> notifications) {
        return new NotificationHandlingResult(notifications, null, null);
    }
}
