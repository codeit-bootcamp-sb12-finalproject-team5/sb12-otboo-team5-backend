package com.codeit.otboo.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.codeit.otboo.api.notification.event.NotificationBroadcastListener;
import com.codeit.otboo.api.notification.service.NotificationSseService;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.dto.NotificationDto;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.event.NotificationBroadcastEvent;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationBroadcastListenerTest {
    private final NotificationSseService sse = mock(NotificationSseService.class);
    private final NotificationBroadcastListener listener = new NotificationBroadcastListener(sse);

    @Test
    void rejectsInvalidMessagesWithDomainError() {
        var valid = notification(UUID.randomUUID());
        var events = new NotificationBroadcastEvent[] {
                null, new NotificationBroadcastEvent(2, valid),
                new NotificationBroadcastEvent(1, null),
                new NotificationBroadcastEvent(1, notification(null))
        };
        for (var event : events) {
            assertThatThrownBy(() -> listener.receive(event))
                    .isInstanceOfSatisfying(NotificationException.class, error ->
                            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_NOTIFICATION_BROADCAST));
        }
        verifyNoInteractions(sse);
    }

    @Test
    void forwardsValidNotification() {
        var notification = notification(UUID.randomUUID());
        listener.receive(new NotificationBroadcastEvent(1, notification));
        verify(sse).publish(notification);
    }

    private NotificationDto notification(UUID receiverId) {
        return new NotificationDto(UUID.randomUUID(), OffsetDateTime.now(), receiverId,
                "title", "content", NotificationLevel.INFO);
    }
}
