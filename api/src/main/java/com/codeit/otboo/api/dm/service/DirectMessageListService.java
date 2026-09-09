package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.api.dm.dto.DirectMessageListResponse;
import com.codeit.otboo.domain.dm.entity.DirectMessage;
import com.codeit.otboo.domain.dm.entity.DmRoomMember;
import com.codeit.otboo.domain.dm.exception.DmException;
import com.codeit.otboo.domain.dm.repository.DirectMessageListProjection;
import com.codeit.otboo.domain.dm.repository.DirectMessageRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomMemberRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageListService {

    private static final int MESSAGE_PAGE_SIZE = 30;

    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;
    private final DirectMessageRepository directMessageRepository;

    @Transactional(readOnly = true)
    public DirectMessageListResponse getMessages(UUID currentUserId, UUID roomId, UUID cursor) {
        validateRoomExists(roomId);
        DmRoomMember member = findActiveMember(roomId, currentUserId);
        OffsetDateTime joinedAt = member.getJoinedAt();
        DirectMessage cursorMessage = cursor == null ? null : findVisibleCursor(cursor, roomId, joinedAt);

        List<DirectMessageListProjection> results = findMessages(roomId, joinedAt, cursorMessage,
                MESSAGE_PAGE_SIZE + 1);
        boolean hasNext = results.size() > MESSAGE_PAGE_SIZE;
        List<DirectMessageListProjection> page = hasNext
                ? new ArrayList<>(results.subList(0, MESSAGE_PAGE_SIZE)) : results;
        Collections.reverse(page);
        UUID nextCursor = hasNext ? page.get(0).messageId() : null;

        return DirectMessageListResponse.of(roomId, page, nextCursor, hasNext);
    }

    private void validateRoomExists(UUID roomId) {
        if (!dmRoomRepository.existsById(roomId)) {
            throw DmException.roomNotFound();
        }
    }

    private DmRoomMember findActiveMember(UUID roomId, UUID currentUserId) {
        // TODO DM 재입장 구현 시 활성 멤버십의 joinedAt을 재입장 시각으로 갱신해야 한다.
        return dmRoomMemberRepository.findByDmRoom_IdAndUser_IdAndLeftAtIsNull(roomId, currentUserId)
                .orElseThrow(DmException::forbidden);
    }

    // 커서가 같은 방의 입장 이후 메시지인지 검증하고 정렬 위치를 얻는다.
    private DirectMessage findVisibleCursor(UUID cursor, UUID roomId, OffsetDateTime joinedAt) {
        DirectMessage message = directMessageRepository.findByIdAndDmRoom_Id(cursor, roomId)
                .orElseThrow(DmException::invalidCursor);

        if (message.getCreatedAt().isBefore(joinedAt)) {
            throw DmException.invalidCursor();
        }

        return message;
    }

    // size + 1 방식으로 다음 페이지 존재 여부를 계산할 메시지를 조회한다.
    private List<DirectMessageListProjection> findMessages(UUID roomId, OffsetDateTime joinedAt,
                                                            DirectMessage cursor, int limit) {
        PageRequest pageable = PageRequest.of(0, limit);
        if (cursor == null) {
            return directMessageRepository.findLatestMessages(roomId, joinedAt, pageable);
        }
        return directMessageRepository.findMessagesBeforeCursor(roomId, joinedAt, cursor.getCreatedAt(),
                cursor.getId(), pageable);
    }
}
