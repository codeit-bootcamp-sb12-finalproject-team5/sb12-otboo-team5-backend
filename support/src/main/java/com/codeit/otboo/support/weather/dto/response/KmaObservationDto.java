package com.codeit.otboo.support.weather.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

public record KmaObservationDto(
        OffsetDateTime observedAt,
        int nx,
        int ny,
        Map<String, String> categories
) {
    public BigDecimal value(String category, BigDecimal fallback) {
        try {
            String value = categories.get(category);
            return value == null ? fallback : new BigDecimal(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
