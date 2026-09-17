package com.codeit.otboo.domain.notification.event;

import java.util.UUID;

public record WeatherNotificationCreateEvent(
        UUID weatherGridId,
        String title,
        String content,
        UUID afterReceiverId
) {
    public WeatherNotificationCreateEvent(UUID weatherGridId, String title, String content) {
        this(weatherGridId, title, content, null);
    }
}
