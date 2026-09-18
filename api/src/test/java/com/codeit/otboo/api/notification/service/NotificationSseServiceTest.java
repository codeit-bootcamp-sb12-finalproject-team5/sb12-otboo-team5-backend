package com.codeit.otboo.api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.codeit.otboo.api.notification.repository.SseEmitterRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseServiceTest {

    private final SseEmitterRepository emitterRepository = new SseEmitterRepository();
    private final NotificationSseService service = newService();

    @SuppressWarnings("unchecked")
    private NotificationSseService newService() {
        // notification.kafka.enabled=false 이면 브로드캐스트 수신 여부를 보지 않는다.
        return new NotificationSseService(emitterRepository, mock(NotificationService.class),
                mock(ObjectProvider.class), false);
    }

    @Test
    void keepsOnlyTheNewestConnectionPerUser() {
        UUID user = UUID.randomUUID();

        SseEmitter first = service.subscribe(user, null);
        SseEmitter second = service.subscribe(user, null);

        assertThat(emitterRepository.countByReceiver(user)).isEqualTo(1);
        assertThat(emitterRepository.findEmitters(user)).containsExactly(second);
        assertThat(emitterRepository.findEmitters(user)).doesNotContain(first);
    }

    @Test
    void closingOneUserConnectionDoesNotTouchOtherUsers() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        SseEmitter otherConnection = service.subscribe(other, null);

        service.subscribe(user, null);
        service.subscribe(user, null);

        assertThat(emitterRepository.findEmitters(other)).containsExactly(otherConnection);
        assertThat(emitterRepository.count()).isEqualTo(2);
    }

    @Test
    void heartbeatKeepsTheStoredConnection() {
        UUID user = UUID.randomUUID();
        SseEmitter connection = service.subscribe(user, null);

        service.heartbeat();

        assertThat(emitterRepository.findEmitters(user)).containsExactly(connection);
    }
}
