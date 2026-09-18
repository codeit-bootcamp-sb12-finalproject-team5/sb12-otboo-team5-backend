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

    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;

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
            log.warn("[SSE] 구독 거부 receiverId={} 사유=브로드캐스트 수신 준비 안 됨", receiverId);
            throw new NotificationException(ErrorCode.NOTIFICATION_STREAM_UNAVAILABLE);
        }

        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MILLIS);

        emitter.onCompletion(() -> {
            emitterRepository.delete(receiverId, emitter);
            log.debug("[SSE] 연결 종료 receiverId={} 남은 전체 연결={}", receiverId, emitterRepository.count());
        });
        emitter.onTimeout(() -> {
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
            log.info("[SSE] 연결 시간 초과 receiverId={} 제한={}분 (클라이언트가 재연결한다)",
                    receiverId, CONNECTION_TIMEOUT_MILLIS / 60_000L);
        });

        emitter.onError(error -> {
            emitterRepository.delete(receiverId, emitter);
            // 브라우저 종료·네트워크 끊김으로도 발생하므로 예외 내용만 남긴다.
            log.debug("[SSE] 연결 오류 receiverId={} 사유={}", receiverId, error.toString());
        });

        // 초기 메시지를 먼저 보내고 공개하여 알림이 connected보다 앞서지 않도록 한다.
        if (!send(receiverId, emitter, "connected", SseEmitter.event().comment("connected").reconnectTime(3000))) {
            log.warn("[SSE] 연결 시작 실패 receiverId={} 단계=초기 메시지 전송", receiverId);
            return emitter;
        }

        // 새 연결이 살아난 뒤에 정리한다. 반대로 하면 초기 전송이 실패했을 때 멀쩡한 연결만 잃는다.
        closeExistingConnections(receiverId);
        emitterRepository.save(receiverId, emitter);

        log.info("[SSE] 연결 시작 receiverId={} 이 사용자 연결={} 전체 연결={}",
                receiverId, emitterRepository.countByReceiver(receiverId), emitterRepository.count());

        return emitter;
    }

    // 사용자당 연결은 1개만 유지한다. 새로고침으로 버려진 연결은 서버가 바로 알 수 없어,
    // heartbeat 전송이 실패할 때까지 쌓이기 때문이다.
    private void closeExistingConnections(UUID receiverId) {
        for (SseEmitter previous : emitterRepository.findEmitters(receiverId)) {
            emitterRepository.delete(receiverId, previous);
            previous.complete();
            log.info("[SSE] 기존 연결 종료 receiverId={} 사유=사용자당 연결 1개 유지", receiverId);
        }
    }

    /** Kafka 브로드캐스팅 수신부에서 호출할 로컬 연결 전달 진입점. */
    public void publish(NotificationDto notification) {
        var emitters = emitterRepository.findEmitters(notification.receiverId());

        if (emitters.isEmpty()) {
            // 이 서버에 접속 중이 아니면 실시간 전달만 생략된다. 알림 자체는 목록 API로 조회한다.
            log.debug("[SSE] 전달 대상 연결 없음 receiverId={} notificationId={}",
                    notification.receiverId(), notification.id());
            return;
        }

        int delivered = 0;
        for (SseEmitter emitter : emitters) {
            if (send(notification.receiverId(), emitter, "notification",
                    SseEmitter.event().name("notifications")
                            .id(notification.id().toString()).data(notification))) {
                delivered++;
            }
        }

        log.debug("[SSE] 알림 전달 receiverId={} notificationId={} 성공={}/{}",
                notification.receiverId(), notification.id(), delivered, emitters.size());
    }

    // 주의: 아래 10분은 끊긴 연결이 언제 정리되는지 확인하려는 테스트용 값이다.
    // 배포 전에 25초로 되돌린다. 프록시(ALB 기본 유휴 60초)보다 간격이 길면 연결이 그대로 끊긴다.
    // fixedDelay는 "이전 실행이 끝난 뒤"부터 재므로 전송이 느려지면 실제 주기가 늘어난다. fixedRate를 쓴다.
    @Scheduled(initialDelay = 10, fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void heartbeat() {
        int sent = 0;
        int failed = 0;

        for (var connections : emitterRepository.findAll().entrySet()) {
            for (SseEmitter emitter : connections.getValue()) {
                if (send(connections.getKey(), emitter, "heartbeat", SseEmitter.event().comment("heartbeat"))) {
                    sent++;
                } else {
                    failed++;
                }
            }
        }

        // 정상일 때 25초마다 로그가 쌓이지 않도록, 끊긴 연결이 있을 때만 수준을 올린다.
        if (failed > 0) {
            log.info("[SSE] heartbeat 전송 성공={} 실패={} (실패한 연결은 정리함)", sent, failed);
        } else if (sent > 0) {
            log.debug("[SSE] heartbeat 전송 성공={}", sent);
        }
    }

    private boolean send(UUID receiverId, SseEmitter emitter, String kind, SseEmitter.SseEventBuilder event) {
        // heartbeat와 Kafka 수신 스레드의 동일 연결 전송이 겹치지 않도록
        synchronized (emitter) {
            try {
                emitter.send(event);
                return true;
            } catch (IOException | IllegalStateException e) {
                emitterRepository.delete(receiverId, emitter);
                emitter.completeWithError(e);
                // 클라이언트가 먼저 끊어도 발생하므로 예외 종류와 메시지만 남기고, 스택은 debug에서 본다.
                log.warn("[SSE] 전송 실패로 연결 정리 receiverId={} 종류={} 사유={}: {}",
                        receiverId, kind, e.getClass().getSimpleName(), e.getMessage());
                log.debug("[SSE] 전송 실패 상세 receiverId={}", receiverId, e);
                return false;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        int closing = emitterRepository.count();

        emitterRepository.findAll().forEach((receiverId, emitters) -> emitters.forEach(emitter -> {
            emitterRepository.delete(receiverId, emitter);
            emitter.complete();
        }));

        log.info("[SSE] 서버 종료로 연결 정리 연결수={}", closing);
    }
}
