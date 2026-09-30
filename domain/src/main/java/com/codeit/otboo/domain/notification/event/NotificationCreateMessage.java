package com.codeit.otboo.domain.notification.event;

import com.codeit.otboo.domain.notification.entity.NotificationType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationCreateMessage<T>(
        UUID eventId,
        int schemaVersion,
        NotificationType type,
        OffsetDateTime occurredAt,
        String deduplicationKey,
        T payload
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
}
