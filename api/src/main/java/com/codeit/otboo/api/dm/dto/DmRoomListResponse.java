package com.codeit.otboo.api.dm.dto;

import com.codeit.otboo.domain.dm.repository.DmRoomListProjection;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Todo: unreadCount 필드 추후 추가해야함
public record DmRoomListResponse(
    List<DmRoomListItem> data,
    String nextCursor,
    boolean hasNext
) {
    public static DmRoomListResponse from(List<DmRoomListProjection> projections,
                                          Map<UUID, String> profileImageUrls,
                                          String nextCursor, boolean hasNext) {
        return new DmRoomListResponse(
            projections.stream().map(projection -> DmRoomListItem.from(projection, profileImageUrls)).toList(),
            nextCursor,
            hasNext
        );
    }

    private record DmRoomListItem(
        UUID roomId,
        String dmKey,
        Opponent opponent,
        LastMessage lastMessage
    ) {
        private static DmRoomListItem from(DmRoomListProjection projection,
                                           Map<UUID, String> profileImageUrls) {
            return new DmRoomListItem(
                projection.roomId(),
                projection.dmKey(),
                Opponent.from(projection, profileImageUrls),
                LastMessage.from(projection)
            );
        }
    }

    private record Opponent(UUID id, String name, String profileImageUrl) {
        private static Opponent from(DmRoomListProjection projection, Map<UUID, String> profileImageUrls) {
            return new Opponent(
                projection.opponentId(),
                projection.opponentName(),
                profileImageUrls.get(projection.opponentId())
            );
        }
    }

    private record LastMessage(String content, OffsetDateTime sentAt) {
        private static LastMessage from(DmRoomListProjection projection) {
            return new LastMessage(
                projection.lastMessageContent(),
                projection.lastMessageAt()
            );
        }
    }
}
