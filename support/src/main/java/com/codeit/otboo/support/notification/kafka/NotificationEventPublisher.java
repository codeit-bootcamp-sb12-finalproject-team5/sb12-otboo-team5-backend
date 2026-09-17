package com.codeit.otboo.support.notification.kafka;

import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;

public class NotificationEventPublisher {
    private final KafkaTemplate<String, Object> template;

    public NotificationEventPublisher(KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    public CompletableFuture<Void> publishCreate(String key, NotificationCreateMessage<?> message) {
        return send(NotificationTopics.CREATE, key, message);
    }

    public CompletableFuture<Void> publishBroadcast(NotificationBroadcastEvent event) {
        return send(NotificationTopics.BROADCAST, event.notification().receiverId().toString(), event);
    }

    private CompletableFuture<Void> send(String topic, String key, Object message) {
        try {
            return template.send(topic, key, message).thenApply(result -> null);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }
}
