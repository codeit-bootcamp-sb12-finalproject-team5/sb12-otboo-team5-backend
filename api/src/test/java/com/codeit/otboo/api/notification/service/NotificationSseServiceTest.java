package com.codeit.otboo.api.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @Test
    void closesAllEmittersEvenWhenOneAlreadyRecycledAndIsIdempotent() {
        var broken = mock(SseEmitter.class);
        var healthy = mock(SseEmitter.class);
        doThrow(new IllegalStateException("recycled")).when(broken).complete();
        emitterRepository.save(UUID.randomUUID(), broken);
        emitterRepository.save(UUID.randomUUID(), healthy);
        service.shutdown();
        service.shutdown();
        assertThat(emitterRepository.count()).isZero();
        verify(broken, times(1)).complete();
        verify(healthy, times(1)).complete();
        assertThatThrownBy(() -> service.subscribe(UUID.randomUUID(), null))
                .isInstanceOf(com.codeit.otboo.domain.notification.exception.NotificationException.class);
    }

    @Test
    void ignoresChildContextClosureAndClosesOnOwnContextEvent() {
        var own = mock(org.springframework.context.ApplicationContext.class);
        var child = mock(org.springframework.context.ApplicationContext.class);
        service.setApplicationContext(own);
        service.subscribe(UUID.randomUUID(), null);
        service.onContextClosed(new org.springframework.context.event.ContextClosedEvent(child));
        assertThat(emitterRepository.count()).isEqualTo(1);
        service.onContextClosed(new org.springframework.context.event.ContextClosedEvent(own));
        assertThat(emitterRepository.count()).isZero();
    }

    @Test
    void heartbeatDoesNotSendAfterShutdown() throws Exception {
        var emitter = mock(SseEmitter.class);
        emitterRepository.save(UUID.randomUUID(), emitter);
        service.shutdown();
        service.heartbeat();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shutdownCannotMissAConnectionBeingRegistered() throws Exception {
        var repository = spy(new SseEmitterRepository());
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new AssertionError("registration not released");
            }
            return invocation.callRealMethod();
        }).when(repository).save(any(), any());
        @SuppressWarnings("unchecked")
        ObjectProvider<KafkaListenerEndpointRegistry> providers = mock(ObjectProvider.class);
        var target = new NotificationSseService(repository, mock(NotificationService.class), providers, false);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var subscribe = executor.submit(() -> target.subscribe(UUID.randomUUID(), null));
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var close = executor.submit(target::shutdown);
            release.countDown();
            subscribe.get(3, java.util.concurrent.TimeUnit.SECONDS);
            close.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(repository.count()).isZero();
            assertThatThrownBy(() -> target.subscribe(UUID.randomUUID(), null))
                    .isInstanceOf(com.codeit.otboo.domain.notification.exception.NotificationException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void recycledResponseDuringErrorCompletionDoesNotEscape() throws Exception {
        var emitter = mock(SseEmitter.class);
        doThrow(new java.io.IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        doThrow(new IllegalStateException("recycled")).when(emitter).completeWithError(any());
        emitterRepository.save(UUID.randomUUID(), emitter);
        service.heartbeat();
        assertThat(emitterRepository.count()).isZero();
    }

}
