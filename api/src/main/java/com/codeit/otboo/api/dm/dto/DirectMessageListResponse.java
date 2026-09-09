package com.codeit.otboo.api.dm.dto;

import com.codeit.otboo.domain.dm.repository.DirectMessageListProjection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record DirectMessageListResponse(
    UUID roomId,
    List<Message> messages,
    UUID nextCursor,
    boolean hasNext
) {
    public static DirectMessageListResponse of(UUID roomId, List<DirectMessageListProjection> messages,
                                               UUID nextCursor, boolean hasNext) {
        return new DirectMessageListResponse(
            roomId,
            messages.stream().map(Message::from).toList(),
            nextCursor,
            hasNext
        );
    }

    public record Message(UUID messageId, UUID senderId, String content, OffsetDateTime createdAt) {
        private static Message from(DirectMessageListProjection projection) {
            return new Message(
                projection.messageId(),
                projection.senderId(),
                projection.content(),
                projection.createdAt()
            );
        }
    }
}
