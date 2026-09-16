package com.codeit.otboo.domain.notification.event;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WeatherNotificationCreateEvent(
        UUID gridId,
        String precipitationType,
        OffsetDateTime firstRainAt,
        UUID afterReceiverId
) {
    public WeatherNotificationCreateEvent(UUID gridId, String precipitationType, OffsetDateTime firstRainAt) {
        this(gridId, precipitationType, firstRainAt, null);
    }
}
