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
    private final com.codeit.otboo.api.notification.repository.SseEmitterRepository emitters =
            mock(com.codeit.otboo.api.notification.repository.SseEmitterRepository.class);
    private final com.codeit.otboo.api.notification.service.NotificationService notifications =
            mock(com.codeit.otboo.api.notification.service.NotificationService.class);
    private final NotificationBroadcastListener listener = new NotificationBroadcastListener(sse, emitters, notifications);

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

    @Test
    void offlineBatchDoesNotQueryDatabase() {
        listener.receive(NotificationBroadcastEvent.of(java.util.List.of(notification(UUID.randomUUID()))));
        verifyNoInteractions(notifications, sse);
    }

    @Test
    void batchQueriesOnlyLocalConnectionsAndChecksDatabaseReceiver() {
        var local = notification(UUID.randomUUID());
        var offline = notification(UUID.randomUUID());
        when(emitters.countByReceiver(local.receiverId())).thenReturn(1);
        when(notifications.findForDelivery(java.util.List.of(local.id()))).thenReturn(java.util.List.of(local));
        listener.receive(NotificationBroadcastEvent.of(java.util.List.of(local, offline)));
        verify(notifications).findForDelivery(java.util.List.of(local.id()));
        verify(sse).publish(local);
        verify(sse, never()).publish(offline);
    }

    @Test
    void mismatchedDatabaseReceiverIsNotDelivered() {
        var ref = notification(UUID.randomUUID());
        when(emitters.countByReceiver(ref.receiverId())).thenReturn(1);
        var stored = new NotificationDto(ref.id(), ref.createdAt(), UUID.randomUUID(),
                ref.title(), ref.content(), ref.level());
        when(notifications.findForDelivery(java.util.List.of(ref.id()))).thenReturn(java.util.List.of(stored));
        listener.receive(NotificationBroadcastEvent.of(ref));
        verifyNoInteractions(sse);
    }

    @Test
    void duplicateReferencesAreRejectedBeforeDatabaseAccess() {
        var value = notification(UUID.randomUUID());
        assertThatThrownBy(() -> listener.receive(NotificationBroadcastEvent.of(java.util.List.of(value, value))))
                .isInstanceOf(NotificationException.class);
        verifyNoInteractions(notifications, sse);
    }

    private NotificationDto notification(UUID receiverId) {
        return new NotificationDto(UUID.randomUUID(), OffsetDateTime.now(), receiverId,
                "title", "content", NotificationLevel.INFO);
    }
}
