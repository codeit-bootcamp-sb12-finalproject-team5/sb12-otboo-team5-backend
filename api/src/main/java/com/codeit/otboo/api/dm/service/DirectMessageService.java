package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.api.dm.dto.DirectMessageResponse;
import com.codeit.otboo.domain.dm.entity.DirectMessage;
import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.exception.DmException;
import com.codeit.otboo.domain.dm.repository.DirectMessageRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomMemberRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;

    // 인증된 사용자가 DM 방의 상대방에게 메시지를 저장하고 방의 마지막 메시지 시각을 갱신한다.
    @Transactional
    public DirectMessageResponse sendMessage(UUID currentUserId, UUID roomId, UUID receiverId, String content) {
        validateContent(content);

        DmRoom room = findRoom(roomId);
        validateMembers(roomId, currentUserId, receiverId);

        User sender = userRepository.getReferenceById(currentUserId);
        DirectMessage message = directMessageRepository.save(DirectMessage.builder()
                .dmRoom(room)
                .sender(sender)
                .content(content)
                .build());

        room.updateLastMessageAt(message.getCreatedAt());

        return DirectMessageResponse.from(message);
    }

    private void validateContent(String content) {
        if (content == null || content.isEmpty()) {
            throw DmException.invalidMessage();
        }
    }

    private DmRoom findRoom(UUID roomId) {
        return dmRoomRepository.findById(roomId)
            .orElseThrow(DmException::roomNotFound);
    }

    private void validateMembers(UUID roomId, UUID currentUserId, UUID receiverId) {
        if (currentUserId.equals(receiverId)
            || !dmRoomMemberRepository.existsByDmRoom_IdAndUser_IdAndLeftAtIsNull(roomId, currentUserId)
            || !dmRoomMemberRepository.existsByDmRoom_IdAndUser_IdAndLeftAtIsNull(roomId, receiverId)) {
            throw DmException.forbidden();
        }
    }

}
