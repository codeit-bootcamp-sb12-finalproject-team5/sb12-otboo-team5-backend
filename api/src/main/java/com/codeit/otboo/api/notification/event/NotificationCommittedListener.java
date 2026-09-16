package com.codeit.otboo.api.notification.event;

import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.NotificationSourceEvent;
import com.codeit.otboo.domain.notification.event.RoleChangedNotificationEvent;
import com.codeit.otboo.domain.notification.event.DirectMessageNotificationEvent;
import com.codeit.otboo.domain.notification.event.FeedNotificationCreateEvent;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationCommittedListener {
    private final NotificationEventPublisher publisher;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(NotificationCreateMessage<?> message) {
        try {
            String key;

            if (message.payload() instanceof RoleChangedNotificationEvent payload) {
                key = payload.receiverId().toString();
            } else if (message.payload() instanceof DirectMessageNotificationEvent payload) {
                key = payload.receiverId().toString();
            } else if (message.payload() instanceof NotificationSourceEvent payload) {
                key = payload.sourceId().toString();
            } else if (message.payload() instanceof FeedNotificationCreateEvent payload) {
                key = payload.feedId().toString();
            } else {
                return;
            }

            publisher.publishCreate(key, message)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            log.error("NOTIFICATION_CREATE_FAILED eventId={} type={} deduplicationKey={}",
                                    message.eventId(), message.type(), message.deduplicationKey(), error);
                        }
                    });
        } catch (RuntimeException error) {
            // 이미 커밋된 업무의 응답을 Kafka 실패 때문에 실패 응답으로 바꾸지 않는다.
            log.error("NOTIFICATION_CREATE_FAILED eventId={}", message.eventId(), error);
        }
    }
}
