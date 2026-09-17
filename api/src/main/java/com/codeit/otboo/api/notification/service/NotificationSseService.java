package com.codeit.otboo.api.notification.service;

import com.codeit.otboo.api.notification.repository.SseEmitterRepository;
import com.codeit.otboo.domain.notification.dto.NotificationDto;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class NotificationSseService {

    private final BooleanSupplier ready;
    private final SseEmitterRepository emitterRepository;

    public NotificationSseService(
            SseEmitterRepository emitterRepository,
            ObjectProvider<KafkaListenerEndpointRegistry> registries,
            @Value("${notification.kafka.enabled:false}") boolean kafkaEnabled) {
        this.emitterRepository = emitterRepository;

        this.ready = () -> {
            if (!kafkaEnabled) return true;
            var registry = registries.getIfAvailable();
            var container = registry == null ? null : registry.getListenerContainer("notification-broadcast");
            return container != null && container.isRunning()
                    && container.getAssignedPartitions() != null
                    && !container.getAssignedPartitions().isEmpty();
        };


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

        // 초기 메시지를 먼저 보내고 공개하여 알림이 connected보다 앞서지 않도록 한다.
        if (send(receiverId, emitter, SseEmitter.event().comment("connected").reconnectTime(3000))) {
            emitterRepository.save(receiverId, emitter);
        }

        return emitter;
    }

    /** Kafka 브로드캐스팅 수신부에서 호출할 로컬 연결 전달 진입점. */
    public void publish(NotificationDto notification) {
        emitterRepository.findEmitters(notification.receiverId()).forEach(emitter ->
                send(notification.receiverId(), emitter, SseEmitter.event().name("notifications")
                        .id(notification.id().toString()).data(notification)));
    }

    @Scheduled(initialDelay = 2, fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void heartbeat() {
        emitterRepository.findAll().forEach((receiverId, emitters) -> emitters.forEach(emitter ->
                send(receiverId, emitter, SseEmitter.event().comment("heartbeat"))));
    }

    private boolean send(UUID receiverId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        // heartbeat와 Kafka 수신 스레드의 동일 연결 전송이 겹치지 않도록
        synchronized (emitter) {
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException e) {
                emitterRepository.delete(receiverId, emitter);
                emitter.completeWithError(e);
                return false;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        emitterRepository.findAll().forEach((receiverId, emitters) -> emitters.forEach(emitter -> {
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
        }));
    }
}
