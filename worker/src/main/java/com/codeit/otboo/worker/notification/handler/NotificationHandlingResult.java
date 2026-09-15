package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import java.util.List;

/** 저장이 커밋된 알림과 다음 페이지 인계 요청. 다음 페이지가 없으면 continuation은 null이다. */
public record NotificationHandlingResult(
        List<NotificationDto> notifications,
        NotificationCreateMessage<?> continuation,
        String continuationKey
) {
    public NotificationHandlingResult {
        notifications = List.copyOf(notifications);
    }

    public static NotificationHandlingResult completed(List<NotificationDto> notifications) {
        return new NotificationHandlingResult(notifications, null, null);
    }
}
