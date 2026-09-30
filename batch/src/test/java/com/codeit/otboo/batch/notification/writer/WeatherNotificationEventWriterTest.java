package com.codeit.otboo.batch.notification.writer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.batch.notification.metrics.WeatherNotificationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.*;
import com.codeit.otboo.domain.notification.exception.NotificationException;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

class WeatherNotificationEventWriterTest {
    private final NotificationEventPublisher publisher = mock(NotificationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-16T09:00:00Z"), ZoneOffset.UTC);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WeatherNotificationMetrics metrics = new WeatherNotificationMetrics(registry);
    private final WeatherNotificationEventWriter writer = new WeatherNotificationEventWriter(publisher, clock, metrics);
    private final NotificationCreateMessage<WeatherNotificationCreateEvent> message = new NotificationCreateMessage<>(
            UUID.randomUUID(), 2, NotificationType.WEATHER_FORECAST, OffsetDateTime.now(clock), "WEATHER_FORECAST:2026-09-17",
            new WeatherNotificationCreateEvent(UUID.randomUUID(), "제목", "본문"));

    @Test
    void publishesUsingGridKeyAndPropagatesBrokerFailure() {
        when(publisher.publishCreate(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        writer.write(new Chunk<>(message));
        assertThat(registry.get("otboo.weather.notification.published").counter().count()).isEqualTo(1);
        verify(publisher).publishCreate(message.payload().weatherGridId().toString(), message);
        when(publisher.publishCreate(anyString(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker")));
        assertThatThrownBy(() -> writer.write(new Chunk<>(message))).isInstanceOf(NotificationException.class);
        assertThat(registry.get("otboo.weather.notification.published").counter().count()).isEqualTo(1);
    }

    @Test
    void crossingMidnightDuringProcessingDoesNotPublish() {
        var expired = new WeatherNotificationEventWriter(publisher,
                Clock.fixed(Instant.parse("2026-09-16T15:00:00Z"), ZoneOffset.UTC), metrics);
        assertThatThrownBy(() -> expired.write(new Chunk<>(message))).isInstanceOf(NotificationException.class);
        verifyNoInteractions(publisher);
        assertThat(registry.get("otboo.weather.notification.published").counter().count()).isZero();
    }
}
