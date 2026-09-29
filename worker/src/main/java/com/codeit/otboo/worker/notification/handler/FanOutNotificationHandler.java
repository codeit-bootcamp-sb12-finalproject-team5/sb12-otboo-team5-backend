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
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.HashSet;
import java.util.Objects;
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
    private final NotificationEventPublisher publisher;

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

        if (!LocalDate.now(KST).isBefore(forecastDate)) {
            log.info("[WEATHER-NOTIFICATION] 만료 grid={}, forecastDate={}, cursor={} 필요 작업 종료",
                    payload.weatherGridId(), forecastDate, payload.afterReceiverId());
            return new NotificationHandlingResult(List.of(), null, null);
        }

        if (payload.receiverIds() != null) {
            return savePage(message, payload.receiverIds(), payload.title(), payload.content());
        }

        return split(
            message,
            payload.afterReceiverId(),
            payload.weatherGridId().toString(),
            cursor -> recipients.findGridUsers(payload.weatherGridId(), cursor, PAGE_SIZE + 1),
            ids -> new WeatherNotificationCreateEvent(
                payload.weatherGridId(),
                payload.title(),
                payload.content(),
                null,
                ids
            )
        );
    }

    private NotificationHandlingResult feed(NotificationCreateMessage<JsonNode> message) {
        var payload = payload(message, FeedNotificationCreateEvent.class);
        if (payload.feedId() == null
                || !message.deduplicationKey().equals("FEED_CREATED:" + payload.feedId())) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var source = sources.findFeed(payload.feedId())
                .orElseThrow(() -> new NotificationException(ErrorCode.NOTIFICATION_SOURCE_NOT_FOUND));

        if (payload.receiverIds() != null) {
            return savePage(message, payload.receiverIds(), "새로운 피드가 등록되었습니다",
                    "%s님이 새로운 피드를 등록했습니다.".formatted(source.authorName()));
        }

        return split(
            message,
            payload.afterReceiverId(),
            payload.feedId().toString(),
            cursor -> recipients.findFollowers(source.authorId(), cursor, PAGE_SIZE + 1),
            ids -> new FeedNotificationCreateEvent(
                payload.feedId(),
                null,
                ids
            )
        );
    }

    private NotificationHandlingResult savePage(NotificationCreateMessage<JsonNode> message,
            List<UUID> receivers, String title, String content) {
        if (receivers.isEmpty() || receivers.size() > PAGE_SIZE || receivers.stream().anyMatch(Objects::isNull)
                || new HashSet<>(receivers).size() != receivers.size()) {
            throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return NotificationHandlingResult.completed(saveService.savePage(message.type(),
                message.deduplicationKey(), receivers, title, content, NotificationLevel.INFO));
    }

    private NotificationHandlingResult split(
        NotificationCreateMessage<JsonNode> message,
        UUID cursor,
        String key,
        Function<UUID, List<UUID>> fetch,
        Function<List<UUID>, Object> payloadFactory
    ) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);

        int pages = 0;

        while (true) {
            var page = fetch.apply(cursor);

            if (page.isEmpty()) {
                break;
            }

            var receivers = List.copyOf(page.subList(0, Math.min(page.size(), PAGE_SIZE)));
            var task = new NotificationCreateMessage<>(
                message.eventId(),
                message.schemaVersion(),
                message.type(),
                message.occurredAt(),
                message.deduplicationKey(),
                payloadFactory.apply(receivers)
            );

            try {
                long remaining = deadline - System.nanoTime();

                if (remaining <= 0) {
                    throw new TimeoutException("Fan-out dispatch budget exceeded");
                }

                publisher.publishCreate(key + ":page:" + receivers.get(0), task)
                        .get(Math.min(remaining, TimeUnit.SECONDS.toNanos(12)),
                                TimeUnit.NANOSECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new NotificationException(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED, exception);
            } catch (ExecutionException | TimeoutException
                    | RuntimeException exception) {
                throw new NotificationException(ErrorCode.NOTIFICATION_CONTINUATION_FAILED, exception)
                        .addDetail("eventId", message.eventId());
            }

            pages++;

            if (page.size() <= PAGE_SIZE) {
                break;
            }

            cursor = receivers.get(receivers.size() - 1);
        }

        log.info("[NOTIFICATION-FANOUT] dispatched eventId={} type={} pages={}",
                message.eventId(), message.type(), pages);

        return NotificationHandlingResult.completed(List.of());
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
