package com.codeit.otboo.batch.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WeatherNotificationMetrics {

    private final Counter published;

    public WeatherNotificationMetrics(MeterRegistry registry) {
        published = Counter.builder("otboo.weather.notification.published")
                .description("Weather grid notification events acknowledged by Kafka; includes republishing")
                .register(registry);
    }

    public void recordPublished() {
        published.increment();
    }
}
