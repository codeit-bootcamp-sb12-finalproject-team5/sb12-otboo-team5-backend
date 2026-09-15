package com.codeit.otboo.api.notification.event;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.SingleNotificationCreateEvent;
import com.fasterxml.uuid.Generators;
import com.codeit.otboo.domain.user.entity.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class NotificationEvents {

    private NotificationEvents() {
    }

    public static NotificationCreateMessage<SingleNotificationCreateEvent> roleChanged(UUID receiverId, UserRole role) {
        UUID changeEventId = Generators.timeBasedEpochGenerator().generate();

        return new NotificationCreateMessage<>(
            changeEventId,
            1,
            NotificationType.ROLE_CHANGED,
            OffsetDateTime.now(),
            "ROLE_CHANGED:" + changeEventId,
            new SingleNotificationCreateEvent(
                receiverId,
                "권한이 변경되었습니다",
                "회원님의 권한이 %s(으)로 변경되었습니다. 다시 로그인해 주세요."
                    .formatted(role == UserRole.ADMIN ? "관리자" : "일반 사용자"),
                NotificationLevel.INFO
            )
        );
    }
}
