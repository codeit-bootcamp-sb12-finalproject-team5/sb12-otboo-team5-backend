package com.codeit.otboo.domain.dm.repository;

import com.codeit.otboo.domain.dm.entity.DmRoomMember;
import com.codeit.otboo.domain.dm.entity.DirectMessage;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DmRoomMemberRepository extends JpaRepository<DmRoomMember, UUID> {

    boolean existsByDmRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);

    Optional<DmRoomMember> findByDmRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);

    // 더 최신 메시지일 때만 읽음 위치를 갱신해 동시 요청에서도 위치가 뒤로 가지 않게 한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DmRoomMember member
            set member.lastReadMessage = :message
            where member.id = :memberId
              and (member.lastReadMessage is null
                or exists (
                    select 1 from DirectMessage currentRead
                    where currentRead = member.lastReadMessage
                      and (currentRead.createdAt < :messageCreatedAt
                        or (currentRead.createdAt = :messageCreatedAt
                          and currentRead.id < :messageId))
                ))
            """)
    int updateLastReadMessageIfNewer(
            @Param("memberId") UUID memberId,
            @Param("message") DirectMessage message,
            @Param("messageCreatedAt") OffsetDateTime messageCreatedAt,
            @Param("messageId") UUID messageId);
}
