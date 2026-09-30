package com.codeit.otboo.api.dm.dto;

import com.codeit.otboo.domain.dm.entity.DirectMessage;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DirectMessageResponse(
        UUID messageId,
        UUID roomId,
        UUID senderId,
        String content,
        OffsetDateTime createdAt
) {

    public static DirectMessageResponse from(DirectMessage message) {
        return new DirectMessageResponse(
                message.getId(),
                message.getDmRoom().getId(),
                message.getSender().getId(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
