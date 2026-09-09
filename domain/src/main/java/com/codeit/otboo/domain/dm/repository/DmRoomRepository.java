package com.codeit.otboo.domain.dm.repository;

import com.codeit.otboo.domain.dm.entity.DmRoom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DmRoomRepository extends JpaRepository<DmRoom, UUID> {

    Optional<DmRoom> findByDmKey(String dmKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from DmRoom room where room.id = :roomId")
    Optional<DmRoom> findByIdForUpdate(@Param("roomId") UUID roomId);

    // 현재 사용자의 메시지 있는 DM 방과 상대방, 마지막 메시지를 최근 대화 순으로 커서 조회한다.
    @Query("""
            select room.id as roomId,
                room.dmKey as dmKey,
                opponent.id as opponentId,
                opponent.name as opponentName,
                message.content as lastMessageContent,
                message.createdAt as lastMessageAt
            from DmRoomMember currentMember
            join currentMember.dmRoom room
            join DmRoomMember opponentMember on opponentMember.dmRoom = room
                and opponentMember.user.id <> :userId
            join opponentMember.user opponent
            join DirectMessage message on message.dmRoom = room
            where currentMember.user.id = :userId
              and currentMember.leftAt is null
              and not exists (
                  select 1 from DirectMessage newerMessage
                  where newerMessage.dmRoom = room
                    and (newerMessage.createdAt > message.createdAt
                      or (newerMessage.createdAt = message.createdAt and newerMessage.id > message.id))
              )
              and (:cursorSentAt is null
                or message.createdAt < :cursorSentAt
                or (message.createdAt = :cursorSentAt and room.id < :cursorRoomId))
            order by message.createdAt desc, room.id desc
            """)
    List<DmRoomListRow> findDmRoomRowsByUserId(
            @Param("userId") UUID userId,
            @Param("cursorSentAt") OffsetDateTime cursorSentAt,
            @Param("cursorRoomId") UUID cursorRoomId,
            Pageable pageable);

    default List<DmRoomListProjection> findDmRoomsByUserId(
            UUID userId, OffsetDateTime cursorSentAt, UUID cursorRoomId, Pageable pageable) {
        return findDmRoomRowsByUserId(userId, cursorSentAt, cursorRoomId, pageable).stream()
                .map(DmRoomListProjection::from)
                .toList();
    }
}
