package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.domain.dm.entity.DmRoom;
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
public class DmRoomLeaveService {

    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;
    private final DirectMessageRepository directMessageRepository;

    // 현재 사용자를 방에서 비활성화하고 마지막 활성 멤버라면 방 데이터를 모두 삭제
    @Transactional
    public void leaveRoom(UUID currentUserId, UUID roomId) {
        DmRoom room = dmRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(DmException::roomNotFound);
        DmRoomMember member = dmRoomMemberRepository.findByDmRoom_IdAndUser_IdAndLeftAtIsNull(roomId, currentUserId)
                .orElseThrow(DmException::forbidden);
        member.leave();

        if (dmRoomMemberRepository.countByDmRoom_IdAndLeftAtIsNull(roomId) == 0) {
            deleteRoomData(room);
        }
    }

    // FK 제약을 만족하도록 읽음 포인터, 메시지, 멤버, 방 순서로 삭제
    private void deleteRoomData(DmRoom room) {
        UUID roomId = room.getId();
        dmRoomMemberRepository.clearLastReadMessagesByRoomId(roomId);
        directMessageRepository.deleteAllByRoomId(roomId);
        dmRoomMemberRepository.deleteAllByRoomId(roomId);
        dmRoomRepository.delete(room);
    }
}
