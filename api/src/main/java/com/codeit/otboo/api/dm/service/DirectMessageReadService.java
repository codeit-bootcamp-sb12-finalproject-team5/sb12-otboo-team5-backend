package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.domain.dm.entity.DirectMessage;
import com.codeit.otboo.domain.dm.entity.DmRoomMember;
import com.codeit.otboo.domain.dm.exception.DmException;
import com.codeit.otboo.domain.dm.repository.DirectMessageRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomMemberRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageReadService {

    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;
    private final DirectMessageRepository directMessageRepository;

    // 현재 사용자가 확인한 메시지까지 읽음 위치를 단조 증가 방식으로 갱신한다.
    @Transactional
    public void markAsRead(UUID currentUserId, UUID roomId, UUID lastReadMessageId) {
        validateRoomExists(roomId);
        DmRoomMember member = findActiveMember(roomId, currentUserId);
        DirectMessage message = findMessage(lastReadMessageId);
        validateReadableMessage(message, roomId, member);
        dmRoomMemberRepository.updateLastReadMessageIfNewer(member.getId(), message,
                message.getCreatedAt(), message.getId());
    }

    private void validateRoomExists(UUID roomId) {
        if (!dmRoomRepository.existsById(roomId)) {
            throw DmException.roomNotFound();
        }
    }

    private DmRoomMember findActiveMember(UUID roomId, UUID currentUserId) {
        return dmRoomMemberRepository.findByDmRoom_IdAndUser_IdAndLeftAtIsNull(roomId, currentUserId)
                .orElseThrow(DmException::forbidden);
    }

    // 요청한 읽음 위치 메시지가 존재하는지 조회한다.
    private DirectMessage findMessage(UUID messageId) {
        return directMessageRepository.findById(messageId).orElseThrow(DmException::messageNotFound);
    }

    // 현재 방의 입장 시점 이후 메시지만 읽음 위치로 허용한다.
    private void validateReadableMessage(DirectMessage message, UUID roomId, DmRoomMember member) {
        if (!message.getDmRoom().getId().equals(roomId)
                || message.getCreatedAt().isBefore(member.getJoinedAt())) {
            throw DmException.invalidMessageId();
        }
    }
}
