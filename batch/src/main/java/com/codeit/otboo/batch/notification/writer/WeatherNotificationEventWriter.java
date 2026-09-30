package com.codeit.otboo.batch.notification.writer;

import com.codeit.otboo.batch.notification.metrics.WeatherNotificationMetrics;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.WeatherNotificationCreateEvent;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

@RequiredArgsConstructor
public class WeatherNotificationEventWriter implements
        ItemWriter<NotificationCreateMessage<WeatherNotificationCreateEvent>> {

    private final NotificationEventPublisher publisher;
    private final Clock clock;
    private final WeatherNotificationMetrics metrics;

    @Override
    public void write(Chunk<? extends NotificationCreateMessage<WeatherNotificationCreateEvent>> chunk) {
        for (var message : chunk) {
            // Processor 처리 중 자정을 넘긴 경우도 발행하지 않는다.
            LocalDate target = LocalDate.parse(message.deduplicationKey().substring("WEATHER_FORECAST:".length()));

            if (!LocalDate.now(clock.withZone(ZoneOffset.ofHours(9))).isBefore(target)) {
                throw new NotificationException(ErrorCode.INVALID_INPUT_VALUE)
                        .addDetail("reason", "날씨 알림 발행 기한 만료");
            }

            try {
                publisher.publishCreate(message.payload().weatherGridId().toString(), message)
                        .get(15, TimeUnit.SECONDS);
                metrics.recordPublished();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new NotificationException(ErrorCode.NOTIFICATION_PROCESSING_INTERRUPTED, exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw new NotificationException(ErrorCode.NOTIFICATION_PUBLICATION_FAILED, exception);
            }
        }
    }
}
