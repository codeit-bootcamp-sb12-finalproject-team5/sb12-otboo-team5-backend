package com.codeit.otboo.api.notification;

import com.codeit.otboo.api.notification.dto.request.NotificationReadRequest;
import com.codeit.otboo.api.notification.service.NotificationService;
import com.codeit.otboo.domain.notification.entity.Notification;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.domain.notification.repository.NotificationRepository;
import com.codeit.otboo.domain.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.notification.repository.NotificationQueryRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationQueryRepository queryRepository = mock(NotificationQueryRepository.class);
    private final NotificationService service = new NotificationService(repository, queryRepository);
    private final UUID receiver = UUID.randomUUID();

    @Test
    void mapsRepositoryPageToApiResponse() {
        var first = notification();
        when(queryRepository.findUnread(receiver, null, null, 1))
                .thenReturn(new CursorResponse<>(List.of(first), first.getCreatedAt().toString(),
                        first.getId(), true, 2, "createdAt", "DESCENDING"));
        var result = service.findNotifications(receiver, new NotificationReadRequest(null, null, 1));
        assertThat(result.data()).hasSize(1);
        assertThat(result.nextIdAfter()).isEqualTo(first.getId());
        assertThat(result.nextCursor()).isEqualTo(first.getCreatedAt().toString());
        assertThat(result.hasNext()).isTrue();
        assertThat(result.totalCount()).isEqualTo(2);
    }

    @Test
    void validatesCursorPairAndTimestamp() {
        assertThatThrownBy(() -> service.findNotifications(receiver,
                new NotificationReadRequest("bad", null, 10)))
                .isInstanceOf(NotificationException.class);
        assertThatThrownBy(() -> service.findNotifications(receiver,
                new NotificationReadRequest("bad", UUID.randomUUID(), 10)))
                .isInstanceOf(NotificationException.class);
        verifyNoInteractions(repository, queryRepository);
    }

    @Test
    void passesTimestampAndTieBreakerToRepository() {
        var time = OffsetDateTime.now();
        var id = UUID.randomUUID();
        when(queryRepository.findUnread(receiver, time, id, 10))
                .thenReturn(CursorResponse.last(List.of(), 0, "createdAt", "DESCENDING"));
        var result = service.findNotifications(receiver, new NotificationReadRequest(time.toString(), id, 10));
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
        verify(queryRepository).findUnread(receiver, time, id, 10);
    }

    @Test
    void marksOwnedNotificationReadIdempotentlyWithoutDeleting() {
        var row = notification();
        when(repository.findByIdAndReceiverId(row.getId(), receiver)).thenReturn(Optional.of(row));
        service.readNotification(receiver, row.getId());
        var readAt = row.getReadAt();
        service.readNotification(receiver, row.getId());
        assertThat(readAt).isNotNull();
        assertThat(row.getReadAt()).isEqualTo(readAt);
        verify(repository, never()).delete(any(Notification.class));
    }

    @Test
    void rejectsMissingOrOtherUsersNotification() {
        var id = UUID.randomUUID();
        when(repository.findByIdAndReceiverId(id, receiver)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.readNotification(receiver, id))
                .isInstanceOf(NotificationException.class);
    }

    private Notification notification() {
        var user = mock(User.class);
        when(user.getId()).thenReturn(receiver);
        return Notification.builder().id(UUID.randomUUID()).createdAt(OffsetDateTime.now())
                .receiver(user).title("비 예보").content("내일 비가 옵니다.")
                .level(NotificationLevel.INFO).build();
    }
}
