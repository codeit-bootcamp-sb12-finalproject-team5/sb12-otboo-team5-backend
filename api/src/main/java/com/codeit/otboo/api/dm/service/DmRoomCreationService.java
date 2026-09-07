package com.codeit.otboo.api.dm.service;

import com.codeit.otboo.domain.dm.entity.DmRoom;
import com.codeit.otboo.domain.dm.entity.DmRoomMember;
import com.codeit.otboo.domain.dm.repository.DmRoomMemberRepository;
import com.codeit.otboo.domain.dm.repository.DmRoomRepository;
import com.codeit.otboo.domain.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class DmRoomCreationService {

    private final DmRoomRepository dmRoomRepository;
    private final DmRoomMemberRepository dmRoomMemberRepository;

    // 별도 트랜잭션으로 DM방과 두 참여자를 별도 트랜잭션으로 함께 생성
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DmRoom create(String dmKey, User sender, User receiver) {
        DmRoom room = dmRoomRepository.save(new DmRoom(dmKey));
        dmRoomMemberRepository.saveAll(List.of(
                new DmRoomMember(room, sender),
                new DmRoomMember(room, receiver)
        ));

        return dmRoomRepository.saveAndFlush(room);
    }
}
