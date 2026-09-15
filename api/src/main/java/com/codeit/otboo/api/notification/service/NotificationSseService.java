package com.codeit.otboo.api.notification.service;

import com.codeit.otboo.api.notification.config.NotificationSseExecutorConfig.NotificationSseExecutors;
import com.codeit.otboo.api.notification.repository.SseEmitterRepository;
import com.codeit.otboo.domain.notification.dto.NotificationDto;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class NotificationSseService {

    private final BooleanSupplier ready;
    private final NotificationSseExecutors executors;
    private final SseEmitterRepository emitterRepository;
    private final ScheduledFuture<?> heartbeatTask;

    public NotificationSseService(NotificationSseExecutors executors,
            SseEmitterRepository emitterRepository,
            ObjectProvider<KafkaListenerEndpointRegistry> registries,
            @Value("${notification.kafka.enabled:false}") boolean kafkaEnabled) {
        this.executors = executors;
        this.emitterRepository = emitterRepository;

        this.ready = () -> {
            if (!kafkaEnabled) return true;
            var registry = registries.getIfAvailable();
            var container = registry == null ? null : registry.getListenerContainer("notification-broadcast");
            return container != null && container.isRunning()
                    && container.getAssignedPartitions() != null
                    && !container.getAssignedPartitions().isEmpty();
        };

        heartbeatTask = executors.heartbeat().scheduleAtFixedRate(() ->
                emitterRepository.findAll().forEach((receiverId, emitters) -> emitters.forEach(emitter ->
                        enqueue(receiverId, emitter, SseEmitter.event().comment("heartbeat")))),
                25, 25, TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(UUID receiverId) {
        if (!ready.getAsBoolean()) {
            throw new NotificationException(ErrorCode.NOTIFICATION_STREAM_UNAVAILABLE);
        }

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> emitterRepository.delete(receiverId, emitter));
        emitter.onTimeout(() -> {
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
        });

        emitter.onError(error -> emitterRepository.delete(receiverId, emitter));
        emitterRepository.save(receiverId, emitter);
        send(receiverId, emitter, SseEmitter.event().comment("connected").reconnectTime(3000));

        return emitter;
    }

    /** Kafka 브로드캐스팅 수신부에서 호출할 로컬 연결 전달 진입점. */
    public void publish(NotificationDto notification) {
        emitterRepository.findEmitters(notification.receiverId()).forEach(emitter ->
                enqueue(notification.receiverId(), emitter, SseEmitter.event().name("notifications")
                        .id(notification.id().toString()).data(notification)));
    }

    private void enqueue(UUID receiverId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            int senderIndex = Math.floorMod(System.identityHashCode(emitter), executors.senders().size());

            executors.senders().get(senderIndex).execute(() -> {
                if (emitterRepository.findEmitters(receiverId).contains(emitter)) {
                    send(receiverId, emitter, event);
                }
            });
        } catch (RejectedExecutionException error) {
            log.warn("SSE queue full; disconnecting emitter for receiverId={}", receiverId);
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
        }
    }

    private void send(UUID receiverId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitterRepository.delete(receiverId, emitter);
            emitter.completeWithError(e);
        }
    }

    @PreDestroy
    public void shutdown() {
        heartbeatTask.cancel(true);
        emitterRepository.findAll().forEach((receiverId, emitters) -> emitters.forEach(emitter -> {
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
        }));
    }
}
