package com.codeit.otboo.domain.dm.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

/** JPQL alias 결과를 domain 조회 projection으로 변환하기 위한 내부 인터페이스다. */
public interface DmRoomListRow {

    UUID getRoomId();

    String getDmKey();

    UUID getOpponentId();

    String getOpponentName();

    String getLastMessageContent();

    OffsetDateTime getLastMessageAt();
}
