package com.codeit.otboo.domain.dm.repository;

import com.codeit.otboo.domain.dm.entity.DirectMessage;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    Optional<DirectMessage> findByIdAndDmRoom_Id(UUID messageId, UUID roomId);

    // 현재 DM 목록 페이지의 방별 읽지 않은 상대방 메시지 수를 한 번에 집계한다.
    @Query("""
            select member.dmRoom.id as roomId, count(message) as unreadCount
            from DmRoomMember member
            join DirectMessage message on message.dmRoom = member.dmRoom
            left join member.lastReadMessage readMessage
            where member.user.id = :userId
              and member.leftAt is null
              and member.dmRoom.id in :roomIds
              and message.createdAt >= member.joinedAt
              and message.sender.id <> :userId
              and (readMessage is null
                or message.createdAt > readMessage.createdAt
                or (message.createdAt = readMessage.createdAt and message.id > readMessage.id))
            group by member.dmRoom.id
            """)
    List<DmUnreadCountProjection> countUnreadMessagesByRoomIds(
            @Param("userId") UUID userId,
            @Param("roomIds") List<UUID> roomIds);

    // 입장 시점 이후의 최신 메시지를 페이지 크기만큼 조회한다.
    @Query("""
            select new com.codeit.otboo.domain.dm.repository.DirectMessageListProjection(
                message.id, message.sender.id, message.content, message.createdAt)
            from DirectMessage message
            where message.dmRoom.id = :roomId
              and message.createdAt >= :joinedAt
            order by message.createdAt desc, message.id desc
            """)
    List<DirectMessageListProjection> findLatestMessages(
            @Param("roomId") UUID roomId,
            @Param("joinedAt") OffsetDateTime joinedAt,
            Pageable pageable);

    // 커서보다 이전이면서 입장 시점 이후인 메시지를 최신순으로 조회한다.
    @Query("""
            select new com.codeit.otboo.domain.dm.repository.DirectMessageListProjection(
                message.id, message.sender.id, message.content, message.createdAt)
            from DirectMessage message
            where message.dmRoom.id = :roomId
              and message.createdAt >= :joinedAt
              and (message.createdAt < :cursorCreatedAt
                or (message.createdAt = :cursorCreatedAt and message.id < :cursorId))
            order by message.createdAt desc, message.id desc
            """)
    List<DirectMessageListProjection> findMessagesBeforeCursor(
            @Param("roomId") UUID roomId,
            @Param("joinedAt") OffsetDateTime joinedAt,
            @Param("cursorCreatedAt") OffsetDateTime cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            Pageable pageable);
}
