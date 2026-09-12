package com.codeit.otboo.api.notification.service;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationSseService {
    private record Connection(UUID receiverId, SseEmitter emitter) {}
    private final ConcurrentHashMap<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "notification-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationSseService() {
        heartbeat.scheduleAtFixedRate(() -> connections.forEach((id, connection) ->
                send(id, connection, SseEmitter.event().comment("heartbeat"))),
                25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(UUID receiverId) {
        UUID connectionId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        Connection connection = new Connection(receiverId, emitter);
        connections.put(connectionId, connection);
        emitter.onCompletion(() -> connections.remove(connectionId));
        emitter.onTimeout(() -> {
            connections.remove(connectionId);
            emitter.complete();
        });
        emitter.onError(error -> connections.remove(connectionId));
        send(connectionId, connection, SseEmitter.event().comment("connected").reconnectTime(3000));
        return emitter;
    }

    /** Kafka 브로드캐스팅 수신부에서 호출할 로컬 연결 전달 진입점. */
    public void publish(NotificationDto notification) {
        connections.forEach((id, connection) -> {
            if (connection.receiverId().equals(notification.receiverId())) {
                send(id, connection, SseEmitter.event().name("notifications")
                        .id(notification.id().toString()).data(notification));
            }
        });
    }

    private void send(UUID id, Connection connection, SseEmitter.SseEventBuilder event) {
        try {
            connection.emitter().send(event);
        } catch (IOException | IllegalStateException e) {
            connections.remove(id, connection);
            connection.emitter().completeWithError(e);
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeat.shutdownNow();
        connections.values().forEach(connection -> connection.emitter().complete());
        connections.clear();
    }
}
