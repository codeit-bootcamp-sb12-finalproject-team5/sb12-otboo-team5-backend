package com.codeit.otboo.domain.notification.dto;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import java.util.UUID;

public record NotificationContent(
        UUID receiverId,
        String title,
        String content,
        NotificationLevel level
) {
}
