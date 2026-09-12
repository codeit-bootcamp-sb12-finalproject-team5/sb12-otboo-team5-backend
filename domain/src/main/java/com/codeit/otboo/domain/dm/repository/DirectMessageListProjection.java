package com.codeit.otboo.domain.dm.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DirectMessageListProjection(
        UUID messageId,
        UUID senderId,
        String content,
        OffsetDateTime createdAt
) {
}
