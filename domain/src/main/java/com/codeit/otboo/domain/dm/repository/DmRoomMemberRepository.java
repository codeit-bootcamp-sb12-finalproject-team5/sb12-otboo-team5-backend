package com.codeit.otboo.domain.dm.repository;

import com.codeit.otboo.domain.dm.entity.DmRoomMember;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DmRoomMemberRepository extends JpaRepository<DmRoomMember, UUID> {

    boolean existsByDmRoom_IdAndUser_IdAndLeftAtIsNull(UUID roomId, UUID userId);
}
