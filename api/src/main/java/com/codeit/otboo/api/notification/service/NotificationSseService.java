package com.codeit.otboo.api.notification.service;

import com.codeit.otboo.api.notification.repository.SseEmitterRepository;
import com.codeit.otboo.domain.notification.dto.NotificationDto;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
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
    private static final int REPLAY_LIMIT = 50;

    private final BooleanSupplier ready;
    private final SseEmitterRepository emitterRepository;
    private final NotificationService notificationService;

    public NotificationSseService(
            SseEmitterRepository emitterRepository,
            NotificationService notificationService,
            ObjectProvider<KafkaListenerEndpointRegistry> registries,
            @Value("${notification.kafka.enabled:false}") boolean kafkaEnabled) {
        this.emitterRepository = emitterRepository;
        this.notificationService = notificationService;

        this.ready = () -> {
            if (!kafkaEnabled) return true;
            var registry = registries.getIfAvailable();
            var container = registry == null ? null : registry.getListenerContainer("notification-broadcast");
            return container != null && container.isRunning()
                    && container.getAssignedPartitions() != null
                    && !container.getAssignedPartitions().isEmpty();
        };


    }

    public SseEmitter subscribe(UUID receiverId, UUID lastEventId) {
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
            log.debug("[SSE] 연결 오류 receiverId={} 사유={}", receiverId, error.toString());
        });

        if (!send(receiverId, emitter, "connected", SseEmitter.event().comment("connected").reconnectTime(3000))) {
            log.warn("[SSE] 연결 시작 실패 receiverId={} 단계=초기 메시지 전송", receiverId);
            return emitter;
        }

        closeExistingConnections(receiverId);
        emitterRepository.save(receiverId, emitter);

        log.info("[SSE] 연결 시작 receiverId={} 이 사용자 연결={} 전체 연결={}",
                receiverId, emitterRepository.countByReceiver(receiverId), emitterRepository.count());

        replayMissed(receiverId, lastEventId, emitter);

        return emitter;
    }

    private void replayMissed(UUID receiverId, UUID lastEventId, SseEmitter emitter) {
        if (lastEventId == null) {
            return;
        }

        List<NotificationDto> missed = notificationService.findMissed(receiverId, lastEventId, REPLAY_LIMIT);

        for (NotificationDto notification : missed) {
            if (!send(receiverId, emitter, "replay", notificationEvent(notification))) {
                return;
            }
        }

        if (!missed.isEmpty()) {
            log.info("[SSE] 누락 알림 재전송 receiverId={} 건수={} 기준 id={}",
                    receiverId, missed.size(), lastEventId);
        }
        if (missed.size() == REPLAY_LIMIT) {
            log.warn("[SSE] 재전송 상한({}건) 도달 receiverId={} 나머지는 목록 API로 조회해야 한다",
                    REPLAY_LIMIT, receiverId);
        }
    }

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
            if (send(notification.receiverId(), emitter, "notification", notificationEvent(notification))) {
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

    private SseEmitter.SseEventBuilder notificationEvent(NotificationDto notification) {
        return SseEmitter.event().name("notifications")
                .id(notification.id().toString())
                .data(notification);
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
