package com.codeit.otboo.domain.notification.repository;

import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.notification.entity.Notification;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface NotificationQueryRepository {
    CursorResponse<Notification> findUnread(UUID receiverId, OffsetDateTime cursor,
            UUID idAfter, int limit);
}
