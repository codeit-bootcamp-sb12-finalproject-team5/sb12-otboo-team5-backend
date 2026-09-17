package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.worker.notification.service.NotificationSaveService;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.SingleNotificationCreateEvent;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SingleNotificationHandler implements NotificationRequestHandler {
    private final NotificationSaveService saveService;

    @Override
    public Set<NotificationType> supportedTypes() {
        // 같은 payload를 사용하는 1:1 유형은 이 목록과 API 이벤트 팩토리만 확장
        return Set.of(NotificationType.ROLE_CHANGED);
    }

    @Override
    public List<NotificationDto> handle(NotificationCreateMessage<JsonNode> message) {
        SingleNotificationCreateEvent payload;
        try {
            payload = NotificationKafkaJson.mapper()
                    .convertValue(message.payload(), SingleNotificationCreateEvent.class);
        } catch (IllegalArgumentException exception) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE, exception);
        }

        if (payload == null || payload.receiverId() == null || payload.level() == null) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        requireText(payload.title(), 100);
        requireText(payload.content(), 1000);

        return saveService.save(message.type(), message.deduplicationKey(), payload).stream().toList();
    }

    private static void requireText(String value, int max) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
