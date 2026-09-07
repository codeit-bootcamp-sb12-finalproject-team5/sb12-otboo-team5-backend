package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.api.dm.dto.CreateDmRoomRequest;
import com.codeit.otboo.api.dm.dto.DmRoomResponse;
import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.exception.DmException;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
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
    private final DmRoomCreationService dmRoomCreationService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DmRoomResponse createOrGet(CreateDmRoomRequest request) {
        UUID currentUserId = request.senderId();
        UUID receiverId = request.receiverId();
        if (currentUserId.equals(request.receiverId())) {
            throw DmException.selfNotAllowed();
        }

        User sender = userRepository.findById(currentUserId).orElseThrow(UserException::notFound);
        User receiver = userRepository.findById(receiverId).orElseThrow(UserException::notFound);
        String dmKey = createDmKey(currentUserId, receiverId);

        return dmRoomRepository.findByDmKey(dmKey)
            .map(room -> toResponse(room, receiverId, false))
            .orElseGet(() -> createOrGetAfterConcurrentRequest(dmKey, sender, receiver, receiverId));
    }

    // 두 사용자가 거의 동시에 dm 방 생성을 요청했을 경우 중복 방 생성 막고 기존 방을 정상 반환하기 위한 로직
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

    private DmRoomResponse toResponse(DmRoom room, UUID opponentId, boolean created) {
        return new DmRoomResponse(room.getId(), room.getDmKey(), opponentId, created);
    }
}
