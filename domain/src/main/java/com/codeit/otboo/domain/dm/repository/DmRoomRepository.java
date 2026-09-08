package com.codeit.otboo.domain.dm.repository;

import com.codeit.otboo.domain.dm.entity.DmRoom;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DmRoomRepository extends JpaRepository<DmRoom, UUID> {

    Optional<DmRoom> findByDmKey(String dmKey);
}
