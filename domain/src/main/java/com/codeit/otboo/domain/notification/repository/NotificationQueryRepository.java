package com.codeit.otboo.domain.notification.repository;

import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.notification.entity.Notification;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationQueryRepository {
    CursorResponse<Notification> findUnread(UUID receiverId, OffsetDateTime cursor,
            UUID idAfter, int limit);

    /** lastEventId 이후에 생긴 미읽음 알림을 오래된 순으로 가져온다. */
    List<Notification> findUnreadAfter(UUID receiverId, UUID lastEventId, int limit);
}
