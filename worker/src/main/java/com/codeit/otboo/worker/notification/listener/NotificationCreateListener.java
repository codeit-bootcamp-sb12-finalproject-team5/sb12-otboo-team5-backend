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
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);

        for (var notification : result.notifications()) {
            if (System.nanoTime() >= deadline) {
                log.error("NOTIFICATION_BROADCAST_BUDGET_EXCEEDED eventId={} topic={} partition={} offset={}",
                        message.eventId(), record.topic(), record.partition(), record.offset());
                break; // 남은 실시간 전송은 목록 조회로 보완하고 다음 페이지 인계는 계속한다.
            }

            try {
                publisher.publishBroadcast(NotificationBroadcastEvent.of(notification))
                        .get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new NotificationException(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED, exception);
            } catch (ExecutionException | TimeoutException | RuntimeException exception) {
                // 저장 성공 후 실시간 전달 실패는 목록 조회로 복구한다. 원본은 완료 처리한다.
                log.error("NOTIFICATION_BROADCAST_FAILED notificationId={} eventId={} topic={} partition={} offset={}",
                        notification.id(), message.eventId(), record.topic(), record.partition(),
                        record.offset(), exception);
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
