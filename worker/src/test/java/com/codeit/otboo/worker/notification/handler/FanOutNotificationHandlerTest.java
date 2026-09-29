package com.codeit.otboo.worker.notification.handler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.*;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.codeit.otboo.worker.notification.repository.NotificationRecipientRepository;
import com.codeit.otboo.worker.notification.service.NotificationSaveService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FanOutNotificationHandlerTest {
    private final NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
    private final NotificationSaveService save = mock(NotificationSaveService.class);
    private final com.codeit.otboo.worker.notification.repository.NotificationSourceRepository sources =
            mock(com.codeit.otboo.worker.notification.repository.NotificationSourceRepository.class);
    private final com.codeit.otboo.support.notification.kafka.NotificationEventPublisher publisher =
            mock(com.codeit.otboo.support.notification.kafka.NotificationEventPublisher.class);
    private final FanOutNotificationHandler handler = new FanOutNotificationHandler(recipients, save, sources, publisher);
    private final UUID grid = UUID.randomUUID();

    private NotificationCreateMessage<JsonNode> weather() {
        return new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.WEATHER_FORECAST,
                OffsetDateTime.now(), "WEATHER_FORECAST:" + java.time.LocalDate.now(java.time.ZoneOffset.ofHours(9)).plusDays(1),
                NotificationKafkaJson.mapper().valueToTree(new WeatherNotificationCreateEvent(
                        grid, "내일 날씨 예보입니다. | 서울시 은평구 진관동", "최고 27도, 최저 14도 | 14시부터 비 예정")));
    }

    @Test
    void pageSavesWithoutSplittingAgain() {
        var receiver = UUID.randomUUID();
        var initial = weather();
        var task = new NotificationCreateMessage<JsonNode>(initial.eventId(), 2, initial.type(),
                initial.occurredAt(), initial.deduplicationKey(),
                NotificationKafkaJson.mapper().valueToTree(new WeatherNotificationCreateEvent(
                        grid, "제목", "본문", null, List.of(receiver))));
        when(save.savePage(any(), anyString(), anyList(), anyString(), anyString(), any()))
                .thenReturn(List.of());
        handler.handle(task);
        verify(save).savePage(eq(NotificationType.WEATHER_FORECAST), eq(initial.deduplicationKey()),
                eq(List.of(receiver)), eq("제목"), eq("본문"), any());
        verifyNoInteractions(recipients, publisher);
    }

    @Test
    void expiredInitialAndContinuationDoNotAccessDatabase() {
        var valid = weather();
        for (UUID cursor : java.util.Arrays.asList(null, UUID.randomUUID())) {
            var expired = new NotificationCreateMessage<JsonNode>(valid.eventId(), 2, valid.type(), valid.occurredAt(),
                    "WEATHER_FORECAST:" + java.time.LocalDate.now(java.time.ZoneOffset.ofHours(9)),
                    NotificationKafkaJson.mapper().valueToTree(new WeatherNotificationCreateEvent(grid, "제목", "본문", cursor)));
            assertThat(handler.handle(expired).continuation()).isNull();
        }
        verifyNoInteractions(recipients, save);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 500, 501, 10000})
    void splitsAllRecipientsIntoIndependentPages(int count) {
        var ids = IntStream.rangeClosed(1, count).mapToObj(i -> new UUID(0, i)).toList();
        when(recipients.findGridUsers(eq(grid), any(), eq(501))).thenAnswer(invocation -> {
            UUID cursor = invocation.getArgument(1);
            int start = cursor == null ? 0 : (int) cursor.getLeastSignificantBits();
            return ids.subList(start, Math.min(start + 501, count));
        });
        when(publisher.publishCreate(anyString(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var message = weather();
        handler.handle(message);
        var captor = org.mockito.ArgumentCaptor.forClass(NotificationCreateMessage.class);
        verify(publisher, times((count + 499) / 500)).publishCreate(anyString(), captor.capture());
        var actual = new java.util.ArrayList<UUID>();
        for (var task : captor.getAllValues()) {
            assertThat(task.deduplicationKey()).isEqualTo(message.deduplicationKey());
            var payload = (WeatherNotificationCreateEvent) task.payload();
            assertThat(payload.receiverIds()).hasSizeLessThanOrEqualTo(500);
            actual.addAll(payload.receiverIds());
        }
        assertThat(actual).containsExactlyElementsOf(ids);
        verifyNoInteractions(save);
    }

    @Test
    void partialPublishFailurePropagatesAndReplayUsesSamePageKeys() {
        var ids = IntStream.rangeClosed(1, 501).mapToObj(i -> new UUID(0, i)).toList();
        when(recipients.findGridUsers(grid, null, 501)).thenReturn(ids);
        when(recipients.findGridUsers(grid, ids.get(499), 501)).thenReturn(List.of(ids.get(500)));
        when(publisher.publishCreate(anyString(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("offline")))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var message = weather();
        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOfSatisfying(NotificationException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_CONTINUATION_FAILED));
        handler.handle(message);
        verify(publisher, times(2)).publishCreate(eq(grid + ":page:" + ids.get(0)), any());
        verify(publisher, times(2)).publishCreate(eq(grid + ":page:" + ids.get(500)), any());
        verifyNoInteractions(save);
    }

    @Test
    void feedSplitsFollowersWithLegacyCursor() {
        UUID author = UUID.randomUUID(), feed = UUID.randomUUID(), cursor = UUID.randomUUID();
        var ids = List.of(UUID.randomUUID());
        when(recipients.findFollowers(author, cursor, 501)).thenReturn(ids);
        when(sources.findFeed(feed)).thenReturn(java.util.Optional.of(
                new com.codeit.otboo.worker.notification.repository.NotificationSourceRepository.FeedSource(author, "작성자")));
        when(publisher.publishCreate(anyString(), any()))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        var message = new NotificationCreateMessage<JsonNode>(UUID.randomUUID(), 2, NotificationType.FEED_CREATED,
                OffsetDateTime.now(), "FEED_CREATED:" + feed, NotificationKafkaJson.mapper().valueToTree(
                        new FeedNotificationCreateEvent(feed, cursor)));
        handler.handle(message);
        verify(publisher).publishCreate(eq(feed + ":page:" + ids.get(0)), any());
        verifyNoInteractions(save);
    }

    @Test
    void invalidPayloadAndWrongBusinessKeyNeverAccessDatabase() {
        var valid = weather();
        var wrongKey = new NotificationCreateMessage<>(valid.eventId(), 2, valid.type(), valid.occurredAt(),
                "WEATHER_FORECAST:invalid", valid.payload());
        assertThatThrownBy(() -> handler.handle(wrongKey)).isInstanceOfSatisfying(NotificationException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        var invalid = new NotificationCreateMessage<JsonNode>(valid.eventId(), 2, valid.type(), valid.occurredAt(),
                valid.deduplicationKey(), NotificationKafkaJson.mapper().createObjectNode().put("gridId", "bad"));
        assertThatThrownBy(() -> handler.handle(invalid)).isInstanceOf(NotificationException.class);
        verifyNoInteractions(recipients, save);
    }
}
