package com.codeit.otboo.api.notification.event;

import com.codeit.otboo.api.notification.service.NotificationSseService;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.support.notification.kafka.NotificationTopics;
import com.codeit.otboo.api.notification.repository.SseEmitterRepository;
import com.codeit.otboo.api.notification.service.NotificationService;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class NotificationBroadcastListener {
    private final NotificationSseService sse;
    private final SseEmitterRepository emitters;
    private final NotificationService notifications;

    @KafkaListener(
        id = "notification-broadcast",
        idIsGroup = false,
        topics = NotificationTopics.BROADCAST,
        containerFactory = "notificationBroadcastFactory")
    public void receive(NotificationBroadcastEvent event) {
        if (event == null) {
            throw new NotificationException(ErrorCode.INVALID_NOTIFICATION_BROADCAST);
        }

        if (event.schemaVersion() == 1) {
            var notification = event.notification();

            if (notification == null || notification.id() == null || notification.receiverId() == null
                    || notification.createdAt() == null || notification.title() == null
                    || notification.content() == null || notification.level() == null) {
                throw new NotificationException(ErrorCode.INVALID_NOTIFICATION_BROADCAST);
            }

            sse.publish(notification);
            return;
        }

        if (event.schemaVersion() != 2 || event.notification() != null || event.notifications() == null
                || event.notifications().isEmpty() || event.notifications().size() > 500) {
            throw new NotificationException(ErrorCode.INVALID_NOTIFICATION_BROADCAST);
        }

        var selected = new LinkedHashMap<UUID, UUID>();
        var seen = new HashSet<UUID>();

        for (var ref : event.notifications()) {
            if (ref == null || ref.notificationId() == null || ref.receiverId() == null
                    || !seen.add(ref.notificationId())) {
                throw new NotificationException(ErrorCode.INVALID_NOTIFICATION_BROADCAST);
            }

            if (emitters.countByReceiver(ref.receiverId()) > 0) {
                selected.put(ref.notificationId(), ref.receiverId());
            }
        }

        if (selected.isEmpty()) {
            return;
        }

        for (var notification : notifications.findForDelivery(List.copyOf(selected.keySet()))) {
            if (notification.receiverId().equals(selected.get(notification.id()))) {
                sse.publish(notification);
            }
        }
    }
}
