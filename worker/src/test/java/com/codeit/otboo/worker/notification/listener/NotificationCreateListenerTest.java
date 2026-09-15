package com.codeit.otboo.worker.notification.listener;

import com.codeit.otboo.worker.notification.handler.NotificationRequestHandler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class NotificationCreateListenerTest {
    private final NotificationRequestHandler handler = mock(NotificationRequestHandler.class);
    private final NotificationEventPublisher publisher = mock(NotificationEventPublisher.class);

    private NotificationCreateListener listener() {
        when(handler.supportedTypes()).thenReturn(java.util.Set.of(NotificationType.ROLE_CHANGED));
        return new NotificationCreateListener(List.of(handler), publisher);
    }

    private ConsumerRecord<String, NotificationCreateMessage<JsonNode>> record() {
        return new ConsumerRecord<>("notification-create", 0, 1, "receiver",
                new NotificationCreateMessage<>(UUID.randomUUID(), 1, NotificationType.ROLE_CHANGED,
                        OffsetDateTime.now(), "ROLE_CHANGED:business-1",
                        NotificationKafkaJson.mapper().createObjectNode()));
    }

    @Test
    void storageFailureDoesNotBroadcastAndPropagatesForRetry() {
        var listener = listener();
        when(handler.handle(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB down"));
        assertThatThrownBy(() -> listener.receive(record()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void duplicateDoesNotBroadcast() {
        var listener = listener();
        when(handler.handle(any())).thenReturn(List.of());
        listener.receive(record());
        verifyNoInteractions(publisher);
    }

    @Test
    void savedNotificationRemainsSuccessfulWhenBroadcastFails() {
        var listener = listener();
        when(handler.handle(any())).thenReturn(List.of(new NotificationDto(UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), "title", "content", NotificationLevel.INFO)));
        when(publisher.publishBroadcast(any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("broker down")));
        assertThatCode(() -> listener.receive(record())).doesNotThrowAnyException();
        verify(publisher).publishBroadcast(any());
    }

    @Test
    void duplicateHandlerRegistrationFailsAtStartup() {
        when(handler.supportedTypes()).thenReturn(java.util.Set.of(NotificationType.ROLE_CHANGED));
        assertThatThrownBy(() -> new NotificationCreateListener(List.of(handler, handler), publisher))
                .isInstanceOfSatisfying(NotificationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_NOTIFICATION_HANDLER);
                    assertThat(exception.getDetails()).containsEntry("type", NotificationType.ROLE_CHANGED);
                });
    }

    @Test
    void invalidMessageUsesCommonInputError() {
        var listener = listener();
        var invalid = new ConsumerRecord<String, NotificationCreateMessage<JsonNode>>(
                "notification-create", 0, 1, "receiver", null);
        assertThatThrownBy(() -> listener.receive(invalid))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(handler, never()).handle(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void unsupportedTypeUsesNotificationError() {
        var listener = new NotificationCreateListener(List.of(), publisher);
        assertThatThrownBy(() -> listener.receive(record()))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE));
        verifyNoInteractions(publisher);
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptionKeepsCauseAndThreadFlag() throws Exception {
        var listener = listener();
        when(handler.handle(any())).thenReturn(List.of(new NotificationDto(UUID.randomUUID(),
                OffsetDateTime.now(), UUID.randomUUID(), "title", "content", NotificationLevel.INFO)));
        CompletableFuture<Void> pending = mock(CompletableFuture.class);
        var interrupted = new InterruptedException("test interruption");
        when(pending.get(12, java.util.concurrent.TimeUnit.SECONDS)).thenThrow(interrupted);
        when(publisher.publishBroadcast(any())).thenReturn(pending);
        try {
            assertThatThrownBy(() -> listener.receive(record()))
                    .isInstanceOfSatisfying(NotificationException.class, exception -> {
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED);
                        assertThat(exception.getCause()).isSameAs(interrupted);
                    });
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
