package com.codeit.otboo.api.weather.dto.response;

import com.codeit.otboo.domain.weather.entity.SkyStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record WeatherViewData(
    UUID id,
    LocalDateTime forecastedAt,
    LocalDateTime forecastAt,
    SkyStatus skyStatus,
    PrecipitationDto precipitation,
    HumidityDto humidity,
    TemperatureDto temperature,
    WindSpeedDto windSpeed
) {
}
