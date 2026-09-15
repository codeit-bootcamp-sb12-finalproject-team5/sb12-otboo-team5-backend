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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FanOutNotificationHandlerTest {
    private final NotificationRecipientRepository recipients = mock(NotificationRecipientRepository.class);
    private final NotificationSaveService save = mock(NotificationSaveService.class);
    private final com.codeit.otboo.worker.notification.repository.NotificationSourceRepository sources =
            mock(com.codeit.otboo.worker.notification.repository.NotificationSourceRepository.class);
    private final FanOutNotificationHandler handler = new FanOutNotificationHandler(recipients, save, sources);
    private final UUID grid = UUID.randomUUID();

    private NotificationCreateMessage<JsonNode> weather(String time) {
        return new NotificationCreateMessage<>(UUID.randomUUID(), 2, NotificationType.WEATHER_RAIN,
                OffsetDateTime.now(), "WEATHER_RAIN:" + OffsetDateTime.parse(time)
                    .withOffsetSameInstant(java.time.ZoneOffset.ofHours(9)).toLocalDate(),
                NotificationKafkaJson.mapper().valueToTree(
                    new WeatherNotificationCreateEvent(grid, "RAIN", OffsetDateTime.parse(time))));
    }

    @ParameterizedTest
    @CsvSource({"2026-09-13T07:00:00+09:00,9월 13일", "2026-09-12T22:00:00Z,9월 13일",
            "2026-09-12T17:00:00-05:00,9월 13일", "2026-12-31T22:00:00Z,1월 1일"})
    void rainTextUsesKst(String time, String date) {
        var receiver = UUID.randomUUID();
        when(recipients.findGridUsers(grid, null, 501)).thenReturn(List.of(receiver));
        handler.handle(weather(time));
        verify(save).savePage(eq(NotificationType.WEATHER_RAIN), anyString(), eq(List.of(receiver)),
                eq(date + " 비 예보"), eq(date + " 7시부터 비가 올 것으로 예상됩니다."), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 500, 501})
    void oneCallSavesOnlyOnePageAndDuplicatesStillAdvance(int count) {
        var ids = IntStream.rangeClosed(1, count).mapToObj(i -> new UUID(0, i)).toList();
        when(recipients.findGridUsers(grid, null, 501)).thenReturn(ids);
        when(save.savePage(any(), anyString(), anyList(), anyString(), anyString(), any()))
                .thenReturn(List.of()); // 재처리로 모두 중복 저장 제외된 경우
        var message = weather("2026-09-12T22:00:00Z");
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
            assertThat(next.firstRainAt()).isEqualTo(OffsetDateTime.parse("2026-09-12T22:00:00Z"));
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
        var valid = weather("2026-09-12T22:00:00Z");
        var wrongKey = new NotificationCreateMessage<>(valid.eventId(), 2, valid.type(), valid.occurredAt(),
                "WEATHER_RAIN:2026-09-12", valid.payload());
        assertThatThrownBy(() -> handler.handle(wrongKey)).isInstanceOfSatisfying(NotificationException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        var invalid = new NotificationCreateMessage<JsonNode>(valid.eventId(), 2, valid.type(), valid.occurredAt(),
                valid.deduplicationKey(), NotificationKafkaJson.mapper().createObjectNode().put("gridId", "bad"));
        assertThatThrownBy(() -> handler.handle(invalid)).isInstanceOf(NotificationException.class);
        verifyNoInteractions(recipients, save);
    }
}
