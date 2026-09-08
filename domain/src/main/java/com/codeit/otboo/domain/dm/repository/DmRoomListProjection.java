package com.codeit.otboo.domain.dm.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

// API DTO 의존을 방지하기 위한 domain 조회 전용 projection이며, 필드 변경 시 API 응답 DTO 변환부도 함께 수정해야 한다.
public record DmRoomListProjection(
        UUID roomId,
        String dmKey,
        UUID opponentId,
        String opponentName,
        String lastMessageContent,
        OffsetDateTime lastMessageAt
) {
    public static DmRoomListProjection from(DmRoomListRow row) {
        return new DmRoomListProjection(
                row.getRoomId(), row.getDmKey(), row.getOpponentId(), row.getOpponentName(),
                row.getLastMessageContent(), row.getLastMessageAt());
    }
}
