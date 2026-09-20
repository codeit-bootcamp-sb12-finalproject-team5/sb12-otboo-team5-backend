package com.codeit.otboo.support.notification.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.RoleChangedNotificationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationTransportTest {
    @Test
    void roundTripKeepsOffsetAndUsesExplicitPayloadType() throws Exception {
        var mapper = NotificationKafkaJson.mapper();
        var time = OffsetDateTime.parse("2026-09-14T10:30:00+09:00");
        var payload = new RoleChangedNotificationEvent(UUID.randomUUID(), com.codeit.otboo.domain.user.entity.UserRole.ADMIN);
        var message = new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.ROLE_CHANGED,
                time, "ROLE_CHANGED:1", payload);
        var type = mapper.getTypeFactory().constructParametricType(NotificationCreateMessage.class, JsonNode.class);
        NotificationCreateMessage<JsonNode> restored = mapper.readValue(mapper.writeValueAsBytes(message), type);
        assertThat(restored.occurredAt()).isEqualTo(time);
        assertThat(restored.occurredAt().getOffset()).isEqualTo(time.getOffset());
        assertThat(mapper.convertValue(restored.payload(), RoleChangedNotificationEvent.class)).isEqualTo(payload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void callerCanObserveBothImmediateAndAsynchronousFailures() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        var publisher = new NotificationEventPublisher(template);
        when(template.send(anyString(), anyString(), any())).thenThrow(new IllegalStateException("immediate"));
        assertThat(publisher.publishCreate("receiver", message())).isCompletedExceptionally();
        reset(template);
        when(template.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("async")));
        assertThat(publisher.publishCreate("receiver", message())).isCompletedExceptionally();
    }

    private NotificationCreateMessage<String> message() {
        return new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.ROLE_CHANGED,
                OffsetDateTime.now(), "ROLE_CHANGED:1", "payload");
    }
}
