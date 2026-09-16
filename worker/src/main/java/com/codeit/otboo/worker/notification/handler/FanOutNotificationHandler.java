package com.codeit.otboo.worker.notification.handler;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.entity.NotificationLevel;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.FeedNotificationCreateEvent;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.WeatherNotificationCreateEvent;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaJson;
import com.codeit.otboo.worker.notification.repository.NotificationRecipientRepository;
import com.codeit.otboo.worker.notification.repository.NotificationSourceRepository;
import com.codeit.otboo.worker.notification.service.NotificationSaveService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FanOutNotificationHandler implements NotificationRequestHandler {

    static final int PAGE_SIZE = 500;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private final NotificationRecipientRepository recipients;
    private final NotificationSaveService saveService;
    private final NotificationSourceRepository sources;

    @Override
    public Set<NotificationType> supportedTypes() {
        return Set.of(NotificationType.WEATHER_FORECAST, NotificationType.FEED_CREATED);
    }

    @Override
    public NotificationHandlingResult handle(NotificationCreateMessage<JsonNode> message) {
        return switch (message.type()) {
            case WEATHER_FORECAST -> weather(message);
            case FEED_CREATED -> feed(message);
            default -> throw new NotificationException(ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
        };
    }

    private NotificationHandlingResult weather(NotificationCreateMessage<JsonNode> message) {
        var payload = payload(message, WeatherNotificationCreateEvent.class);

        if (payload.weatherGridId() == null || payload.title() == null || payload.title().isBlank()
                || payload.title().length() > 100 || payload.content() == null
                || payload.content().isBlank() || payload.content().length() > 1000) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDate forecastDate;
        try {
            String prefix = "WEATHER_FORECAST:";
            if (message.deduplicationKey() == null || !message.deduplicationKey().startsWith(prefix)) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
            }
            forecastDate = LocalDate.parse(message.deduplicationKey().substring(prefix.length()));
            if (!message.deduplicationKey().equals(prefix + forecastDate)) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
            }
        } catch (DateTimeParseException exception) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE, exception);
        }
        // 최초 요청과 continuation 모두 대상일 00시부터 신규 저장을 중단한다.
        if (!LocalDate.now(KST).isBefore(forecastDate)) {
            log.info("[WEATHER-NOTIFICATION] 만료 grid={}, forecastDate={}, cursor={} 필요 작업 종료",
                    payload.weatherGridId(), forecastDate, payload.afterReceiverId());
            return new NotificationHandlingResult(List.of(), null, null);
        }
        var page = recipients.findGridUsers(payload.weatherGridId(), payload.afterReceiverId(), PAGE_SIZE + 1);
        var receivers = page.subList(0, Math.min(page.size(), PAGE_SIZE));
        Object next = page.size() > PAGE_SIZE ? new WeatherNotificationCreateEvent(
                payload.weatherGridId(), payload.title(), payload.content(),
                receivers.get(receivers.size() - 1)) : null;
        return save(message, receivers, payload.title(), payload.content(), next,
                payload.weatherGridId().toString());
    }

    private NotificationHandlingResult feed(NotificationCreateMessage<JsonNode> message) {
        var payload = payload(message, FeedNotificationCreateEvent.class);

        if (payload.feedId() == null
                || !message.deduplicationKey().equals("FEED_CREATED:" + payload.feedId())) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        var source = sources.findFeed(payload.feedId())
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_SOURCE_NOT_FOUND));
        var page = recipients.findFollowers(source.authorId(), payload.afterReceiverId(), PAGE_SIZE + 1);
        var receivers = page.subList(0, Math.min(page.size(), PAGE_SIZE));

        Object next = page.size() > PAGE_SIZE ? new FeedNotificationCreateEvent(
                payload.feedId(), receivers.get(receivers.size() - 1)) : null;

        return save(
            message,
            receivers,
            "새로운 피드가 등록되었습니다",
            "%s님이 새로운 피드를 등록했습니다.".formatted(source.authorName()),
            next,
            payload.feedId().toString());
    }

    private NotificationHandlingResult save(
        NotificationCreateMessage<JsonNode> message,
        List<UUID> receivers,
        String title,
        String content,
        Object nextPayload,
        String key
    ) {
        var saved = saveService.savePage(
            message.type(),
            message.deduplicationKey(),
            receivers,
            title,
            content,
            NotificationLevel.INFO
        );

        var next = nextPayload == null ? null : new NotificationCreateMessage<>(
            message.eventId(),
            message.schemaVersion(),
            message.type(),
            message.occurredAt(),
            message.deduplicationKey(),
            nextPayload
        );

        return new NotificationHandlingResult(saved, next, next == null ? null : key);
    }

    private <T> T payload(NotificationCreateMessage<JsonNode> message, Class<T> type) {
        try {
            T payload = NotificationKafkaJson.mapper().convertValue(message.payload(), type);

            if (payload == null)
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);

            return payload;
        } catch (IllegalArgumentException exception) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE, exception);
        }
    }
}
