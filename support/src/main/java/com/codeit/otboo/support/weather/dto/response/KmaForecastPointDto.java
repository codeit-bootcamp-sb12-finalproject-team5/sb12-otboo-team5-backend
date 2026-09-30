package com.codeit.otboo.support.weather.dto.response;

import java.time.OffsetDateTime;

public record KmaForecastPointDto(
    OffsetDateTime forecastAt,
    String category,
    String value
) {}
