package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.api.dm.dto.DmRoomResponse;
import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.entity.DmRoomMember;
import com.codeit.otboo.domain.dm.exception.DmException;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomMemberRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.exception.UserException;
import com.codeit.otboo.domain.user.repository.UserRepository;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DmRoomService {

    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;
    private final DmRoomCreationService dmRoomCreationService;
    private final UserRepository userRepository;

    @Transactional
    public DmRoomResponse createOrGet(UUID currentUserId, UUID receiverId) {
        if (currentUserId.equals(receiverId)) {
            throw DmException.selfNotAllowed();
        }

        User sender = findUser(currentUserId);
        User receiver = findUser(receiverId);
        String dmKey = createDmKey(currentUserId, receiverId);

        return dmRoomRepository.findByDmKey(dmKey)
            .map(room -> rejoinIfNeeded(room, currentUserId, receiverId))
            .orElseGet(() -> createOrGetAfterConcurrentRequest(dmKey, sender, receiver, receiverId));
    }

    // 두 사용자가 동시에 DM 방 생성을 요청해도 기존 방을 정상 반환한다.
    private DmRoomResponse createOrGetAfterConcurrentRequest(String dmKey, User sender, User receiver,
                                                             UUID receiverId) {
        try {
            DmRoom room = dmRoomCreationService.create(dmKey, sender, receiver);

            return toResponse(room, receiverId, true);
        } catch (DataIntegrityViolationException e) {
            DmRoom room = dmRoomRepository.findByDmKey(dmKey).orElseThrow(() -> e);

            return toResponse(room, receiverId, false);
        }
    }

    private String createDmKey(UUID firstUserId, UUID secondUserId) {
        String first = firstUserId.toString();
        String second = secondUserId.toString();

        return first.compareTo(second) < 0 ? first + "_" + second : second + "_" + first;
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserException::notFound);
    }

    // 기존 방이지만 나간 사용자가 다시 요청한 경우 새 입장 세션으로 활성화
    private DmRoomResponse rejoinIfNeeded(DmRoom room, UUID currentUserId, UUID receiverId) {
        dmRoomMemberRepository.findByDmRoom_IdAndUser_Id(room.getId(), currentUserId)
                .filter(member -> member.getLeftAt() != null)
                .ifPresent(DmRoomMember::rejoin);

        return toResponse(room, receiverId, false);
    }

    private DmRoomResponse toResponse(DmRoom room, UUID opponentId, boolean created) {
        return new DmRoomResponse(room.getId(), room.getDmKey(), opponentId, created);
    }
}
