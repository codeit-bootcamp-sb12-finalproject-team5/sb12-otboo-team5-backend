package com.codeit.otboo.api.notification.event;

import com.codeit.otboo.api.notification.service.NotificationSseService;
import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.support.notification.kafka.NotificationTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationBroadcastListener {
    private final NotificationSseService sse;

    @KafkaListener(id = "notification-broadcast", idIsGroup = false,
            topics = NotificationTopics.BROADCAST, containerFactory = "notificationBroadcastFactory")
    public void receive(NotificationBroadcastEvent event) {
        if (event == null || event.schemaVersion() != 1 || event.notification() == null
                || event.notification().id() == null || event.notification().receiverId() == null
                || event.notification().createdAt() == null || event.notification().title() == null
                || event.notification().content() == null || event.notification().level() == null) {
            throw new IllegalArgumentException("Invalid notification broadcast");
        }
        sse.publish(event.notification());
    }
}
