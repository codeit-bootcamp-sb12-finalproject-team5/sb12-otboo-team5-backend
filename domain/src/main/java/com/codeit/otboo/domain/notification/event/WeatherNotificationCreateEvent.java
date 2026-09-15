package com.codeit.otboo.domain.notification.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/** afterReceiverId는 worker의 후속 페이지 작업에만 사용한다. 최초 요청은 null이다. */
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
