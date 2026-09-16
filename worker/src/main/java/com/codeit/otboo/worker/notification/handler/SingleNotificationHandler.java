package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.dto.NotificationContent;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.DirectMessageNotificationEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.NotificationSourceEvent;
import com.codeit.otboo.domain.notification.event.RoleChangedNotificationEvent;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.user.entity.UserRole;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.codeit.otboo.worker.notification.repository.NotificationSourceRepository;
import com.codeit.otboo.worker.notification.service.NotificationSaveService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SingleNotificationHandler implements NotificationRequestHandler {
    private final NotificationSaveService saveService;
    private final NotificationSourceRepository sources;

    @Override
    public Set<NotificationType> supportedTypes() {
        return Set.of(
            NotificationType.ROLE_CHANGED,
            NotificationType.FEED_LIKED,
            NotificationType.FEED_COMMENTED,
            NotificationType.FOLLOWED,
            NotificationType.DM_RECEIVED
        );
    }

    @Override
    public NotificationHandlingResult handle(NotificationCreateMessage<JsonNode> message) {
        NotificationContent content;

        if (message.type() == NotificationType.ROLE_CHANGED) {
            var event = payload(message, RoleChangedNotificationEvent.class);

            if (event.receiverId() == null || event.role() == null) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
            }

            content = content(
                event.receiverId(),
                "권한이 변경되었습니다",
                "회원님의 권한이 %s(으)로 변경되었습니다. 다시 로그인해 주세요."
                    .formatted(event.role() == UserRole.ADMIN ? "관리자" : "일반 사용자"));
        } else if (message.type() == NotificationType.DM_RECEIVED) {
            var event = payload(message, DirectMessageNotificationEvent.class);

            requireSource(message, event.messageId());

            if (event.receiverId() == null) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
            }

            var source = sources.findDirectMessage(event.messageId(), event.receiverId())
                    .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_SOURCE_NOT_FOUND));

            content = content(
                source.receiverId(),
                "[DM] %s".formatted(source.actorName()),
                source.content()
            );
        } else {
            var event = payload(message, NotificationSourceEvent.class);

            requireSource(message, event.sourceId());

            var source = switch (message.type()) {
                case FEED_LIKED -> sources.findLike(event.sourceId());
                case FEED_COMMENTED -> sources.findComment(event.sourceId());
                case FOLLOWED -> sources.findFollow(event.sourceId());
                default -> throw new NotificationException(ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
            };

            var value = source
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_SOURCE_NOT_FOUND));

            content = switch (message.type()) {
                case FEED_LIKED -> content(
                    value.receiverId(),
                    "%s님이 내 피드를 좋아합니다.".formatted(value.actorName()),
                    value.content()
                );
                case FEED_COMMENTED -> content(
                    value.receiverId(),
                    "%s님이 댓글을 달았어요.".formatted(value.actorName()),
                    value.content()
                );
                case FOLLOWED -> content(
                    value.receiverId(),
                    "%s님이 나를 팔로우 했어요.".formatted(value.actorName()),
                    ""
                );
                default -> throw new NotificationException(ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
            };
        }

        return NotificationHandlingResult.completed(
                saveService.save(message.type(), message.deduplicationKey(), content).stream()
                    .toList()
        );
    }

    private NotificationContent content(UUID receiverId, String title, String body) {
        if (receiverId == null || title == null || title.isBlank() || body == null) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return new NotificationContent(
            receiverId,
            preview(title, 100),
            preview(body, 1000),
            NotificationLevel.INFO);
    }

    private String preview(String value, int max) {
        return value.codePointCount(0, value.length()) <= max ? value
                : value.substring(0, value.offsetByCodePoints(0, max));
    }

    private void requireSource(NotificationCreateMessage<JsonNode> message, UUID id) {
        if (id == null || !message.deduplicationKey().equals(message.type().name() + ":" + id)) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private <T> T payload(NotificationCreateMessage<JsonNode> message, Class<T> type) {
        try {
            T value = NotificationKafkaJson.mapper().convertValue(message.payload(), type);

            if (value == null) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
            }

            return value;
        } catch (IllegalArgumentException exception) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE, exception);
        }
    }
}
