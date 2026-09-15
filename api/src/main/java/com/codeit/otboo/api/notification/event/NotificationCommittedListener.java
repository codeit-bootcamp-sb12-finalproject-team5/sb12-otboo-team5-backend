package com.codeit.otboo.api.notification.event;

import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.SingleNotificationCreateEvent;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationCommittedListener {
    private final NotificationEventPublisher publisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(NotificationCreateMessage<?> message) {
        // 추후 1:N 요청은 별도 payload 리스너로 확장한다.
        if (!(message.payload() instanceof SingleNotificationCreateEvent payload)) {
            return;
        }
        try {
            publisher.publishCreate(payload.receiverId().toString(), message)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            log.error("NOTIFICATION_CREATE_FAILED eventId={} type={} deduplicationKey={}",
                                    message.eventId(), message.type(), message.deduplicationKey(), error);
                        }
                    });
        } catch (RuntimeException error) {
            // 이미 커밋된 권한 변경의 응답을 Kafka 실패 때문에 실패 응답으로 바꾸지 않는다.
            log.error("NOTIFICATION_CREATE_FAILED eventId={}", message.eventId(), error);
        }
    }
}
