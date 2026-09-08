package com.codeit.otboo.support.weather.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record KmaForecastBundleDto(
        OffsetDateTime forecastedAt,
        int nx,
        int ny,
        List<KmaForecastPointDto> points
) {}
