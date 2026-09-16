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
    private final FanOutNotificationHandler handler = new FanOutNotificationHandler(recipients, save, sources);
    private final UUID grid = UUID.randomUUID();

    private NotificationCreateMessage<JsonNode> weather() {
        return new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.WEATHER_FORECAST,
                OffsetDateTime.now(), "WEATHER_FORECAST:" + java.time.LocalDate.now(java.time.ZoneOffset.ofHours(9)).plusDays(1),
                NotificationKafkaJson.mapper().valueToTree(new WeatherNotificationCreateEvent(
                        grid, "내일 날씨 예보입니다. | 서울시 은평구 진관동", "최고 27도, 최저 14도 | 14시부터 비 예정")));
    }

    @Test
    void preservesBatchTitleAndContent() {
        var receiver = UUID.randomUUID();
        when(recipients.findGridUsers(grid, null, 501)).thenReturn(List.of(receiver));
        handler.handle(weather());
        verify(save).savePage(eq(NotificationType.WEATHER_FORECAST), anyString(), eq(List.of(receiver)),
                eq("내일 날씨 예보입니다. | 서울시 은평구 진관동"), eq("최고 27도, 최저 14도 | 14시부터 비 예정"), any());
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
    @ValueSource(ints = {0, 1, 500, 501})
    void oneCallSavesOnlyOnePageAndDuplicatesStillAdvance(int count) {
        var ids = IntStream.rangeClosed(1, count).mapToObj(i -> new UUID(0, i)).toList();
        when(recipients.findGridUsers(grid, null, 501)).thenReturn(ids);
        when(save.savePage(any(), anyString(), anyList(), anyString(), anyString(), any()))
                .thenReturn(List.of()); // 재처리로 모두 중복 저장 제외된 경우
        var message = weather();
        var result = handler.handle(message);
        verify(recipients, times(1)).findGridUsers(grid, null, 501);
        verify(save).savePage(any(), anyString(), eq(ids.subList(0, Math.min(count, 500))),
                anyString(), anyString(), any());
        assertThat(result.notifications()).isEmpty();
        if (count > 500) {
            assertThat(result.continuation().eventId()).isEqualTo(message.eventId());
            assertThat(result.continuation().deduplicationKey()).isEqualTo(message.deduplicationKey());
            var next = (WeatherNotificationCreateEvent) result.continuation().payload();
            assertThat(next.afterReceiverId()).isEqualTo(ids.get(499));
            assertThat(next.title()).isEqualTo("내일 날씨 예보입니다. | 서울시 은평구 진관동");
            assertThat(next.content()).isEqualTo("최고 27도, 최저 14도 | 14시부터 비 예정");
            assertThat(result.continuationKey()).isEqualTo(grid.toString());
        } else {
            assertThat(result.continuation()).isNull();
        }
    }

    @Test
    void feedUsesFollowersAndPreservesCursor() {
        UUID author = UUID.randomUUID(), feed = UUID.randomUUID(), cursor = UUID.randomUUID();
        var ids = IntStream.rangeClosed(1, 501).mapToObj(i -> new UUID(0, i)).toList();
        when(recipients.findFollowers(author, cursor, 501)).thenReturn(ids);
        when(sources.findFeed(feed)).thenReturn(java.util.Optional.of(
                new com.codeit.otboo.worker.notification.repository.NotificationSourceRepository.FeedSource(author, "작성자")));
        var message = new NotificationCreateMessage<JsonNode>(UUID.randomUUID(), 2, NotificationType.FEED_CREATED,
                OffsetDateTime.now(), "FEED_CREATED:" + feed, NotificationKafkaJson.mapper().valueToTree(
                        new FeedNotificationCreateEvent(feed, cursor)));
        var result = handler.handle(message);
        verify(save).savePage(eq(NotificationType.FEED_CREATED), eq("FEED_CREATED:" + feed),
                eq(ids.subList(0, 500)), eq("새로운 피드가 등록되었습니다"),
                eq("작성자님이 새로운 피드를 등록했습니다."), any());
        assertThat(((FeedNotificationCreateEvent) result.continuation().payload()).afterReceiverId())
                .isEqualTo(ids.get(499));
        assertThat(result.continuationKey()).isEqualTo(feed.toString());
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
