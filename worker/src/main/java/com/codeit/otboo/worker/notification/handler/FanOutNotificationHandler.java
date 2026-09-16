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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FanOutNotificationHandler implements NotificationRequestHandler {

    static final int PAGE_SIZE = 500;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final DateTimeFormatter RAIN_TIME = DateTimeFormatter.ofPattern("M월 d일 H시");
    private final NotificationRecipientRepository recipients;
    private final NotificationSaveService saveService;
    private final NotificationSourceRepository sources;

    @Override
    public Set<NotificationType> supportedTypes() {
        return Set.of(NotificationType.WEATHER_RAIN, NotificationType.FEED_CREATED);
    }

    @Override
    public NotificationHandlingResult handle(NotificationCreateMessage<JsonNode> message) {
        return switch (message.type()) {
            case WEATHER_RAIN -> weather(message);
            case FEED_CREATED -> feed(message);
            default -> throw new NotificationException(ErrorCode.UNSUPPORTED_NOTIFICATION_TYPE);
        };
    }

    private NotificationHandlingResult weather(NotificationCreateMessage<JsonNode> message) {
        var payload = payload(message, WeatherNotificationCreateEvent.class);

        if (payload.gridId() == null || payload.firstRainAt() == null
                || !"RAIN".equals(payload.precipitationType())) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        var rainAt = payload.firstRainAt().withOffsetSameInstant(KST);

        if (!message.deduplicationKey().equals("WEATHER_RAIN:" + rainAt.toLocalDate())) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        var page = recipients.findGridUsers(payload.gridId(), payload.afterReceiverId(), PAGE_SIZE + 1);
        var receivers = page.subList(0, Math.min(page.size(), PAGE_SIZE));

        Object next = page.size() > PAGE_SIZE ? new WeatherNotificationCreateEvent(
                payload.gridId(), payload.precipitationType(), payload.firstRainAt(),
                receivers.get(receivers.size() - 1)) : null;

        return save(
            message,
            receivers,
            "%d월 %d일 비 예보".formatted(rainAt.getMonthValue(), rainAt.getDayOfMonth()),
            "%s부터 비가 올 것으로 예상됩니다.".formatted(rainAt.format(RAIN_TIME)),
            next, payload.gridId().toString());
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
