package com.codeit.otboo.domain.notification.event;

import java.util.UUID;
import java.util.List;

public record WeatherNotificationCreateEvent(
        UUID weatherGridId, String title, String content, UUID afterReceiverId, List<UUID> receiverIds
) {
    public WeatherNotificationCreateEvent(UUID weatherGridId, String title, String content) {
        this(weatherGridId, title, content, null, null);
    }

    public WeatherNotificationCreateEvent(UUID weatherGridId, String title, String content, UUID afterReceiverId) {
        this(weatherGridId, title, content, afterReceiverId, null);
    }
}
