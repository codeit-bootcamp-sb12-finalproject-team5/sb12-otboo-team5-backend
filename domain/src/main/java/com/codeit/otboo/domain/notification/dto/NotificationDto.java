package com.codeit.otboo.domain.notification.dto;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    OffsetDateTime createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {
}
