package com.codeit.otboo.domain.notification.repository;

import com.codeit.otboo.domain.notification.entity.Notification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByIdAndReceiverId(UUID id, UUID receiverId);
}
