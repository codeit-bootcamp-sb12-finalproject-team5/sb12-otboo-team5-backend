package com.codeit.otboo.domain.dm.repository;

import com.codeit.otboo.domain.dm.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {
}
