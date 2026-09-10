package com.codeit.otboo.api.weather.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record WeatherViewData(
    UUID id,
    LocalDateTime forecastedAt,
    LocalDateTime forecastAt,
    String skyStatus,
    PrecipitationDto precipitation,
    HumidityDto humidity,
    TemperatureDto temperature,
    WindSpeedDto windSpeed
) {
}
