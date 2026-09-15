package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.worker.notification.service.NotificationSaveService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SingleNotificationHandlerTest {
    @Test
    void invalidPayloadIsRejectedBeforeDatabaseAccess() throws Exception {
        var save = mock(NotificationSaveService.class);
        var handler = new SingleNotificationHandler(save);
        JsonNode payload = NotificationKafkaJson.mapper().readTree(
                "{\"receiverId\":\""+ UUID.randomUUID() +"\",\"title\":\"\",\"content\":\"test\",\"level\":\"INFO\"}");
        var message = new NotificationCreateMessage<>(UUID.randomUUID(), 1, NotificationType.ROLE_CHANGED,
                OffsetDateTime.now(), "ROLE_CHANGED:1", payload);
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(save);
    }

    @Test
    void malformedPayloadPreservesConversionCause() {
        var save = mock(NotificationSaveService.class);
        var handler = new SingleNotificationHandler(save);
        var payload = NotificationKafkaJson.mapper().createObjectNode().put("receiverId", "invalid-uuid");
        var message = new NotificationCreateMessage<JsonNode>(UUID.randomUUID(), 1,
                NotificationType.ROLE_CHANGED, OffsetDateTime.now(), "ROLE_CHANGED:1", payload);
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOfSatisfying(NotificationException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
                    assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
                });
        verifyNoInteractions(save);
    }
}
