package com.codeit.otboo.worker.notification.listener;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.worker.notification.handler.NotificationRequestHandler;

import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import com.codeit.otboo.support.notification.kafka.NotificationTopics;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationCreateListener {
    private final Map<NotificationType, NotificationRequestHandler> handlers =
            new EnumMap<>(NotificationType.class);

    private final NotificationEventPublisher publisher;

    public NotificationCreateListener(
        List<NotificationRequestHandler> handlers,
        NotificationEventPublisher publisher
    ) {
        this.publisher = publisher;

        for (NotificationRequestHandler handler : handlers) {
            for (NotificationType type : handler.supportedTypes()) {
                if (this.handlers.putIfAbsent(type, handler) != null) {
                    throw new NotificationException(ErrorCode.DUPLICATE_NOTIFICATION_HANDLER)
                            .addDetail("type", type);
                }
            }
        }
    }

    @KafkaListener(topics = NotificationTopics.CREATE, containerFactory = "notificationCreateFactory")
    public void receive(ConsumerRecord<String, NotificationCreateMessage<JsonNode>> record) {
        NotificationCreateMessage<JsonNode> message = record.value();

        if (message == null || message.schemaVersion() != NotificationCreateMessage.CURRENT_SCHEMA_VERSION || message.eventId() == null
                || message.occurredAt() == null || message.type() == null || message.payload() == null
                || message.deduplicationKey() == null || message.deduplicationKey().isBlank()
                || message.deduplicationKey().length() > 200
                || !message.deduplicationKey().startsWith(message.type().name() + ":")) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        NotificationRequestHandler handler = handlers.get(message.type());

        if (handler == null) {
            throw new NotificationException(ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE)
                    .addDetail("type", message.type());
        }
        // handler 내부의 별도 트랜잭션 프록시가 정상 반환한 뒤에만 발행한다.
        var result = handler.handle(message);
        if (!result.notifications().isEmpty()) {
            try {
                publisher.publishBroadcast(NotificationBroadcastEvent.of(result.notifications()))
                        .get(12, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new NotificationException(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED, exception);
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                log.error("NOTIFICATION_BROADCAST_FAILED count={} eventId={} topic={} partition={} offset={}",
                        result.notifications().size(), message.eventId(), record.topic(),
                        record.partition(), record.offset(), exception);
            }
        }

        if (result.continuation() != null) {
            try {
                publisher.publishCreate(result.continuationKey(), result.continuation())
                        .get(12, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new NotificationException(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED, exception);
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                // 재시도 소진 시 원본 topic/partition/offset으로 현재 페이지를 재발행해야 한다.
                throw new NotificationException(ErrorCode.NOTIFICATION_CONTINUATION_FAILED, exception)
                        .addDetail("eventId", message.eventId());
            }
        }
    }
}
