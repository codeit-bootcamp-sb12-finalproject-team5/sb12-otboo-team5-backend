package com.codeit.otboo.api.weather.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WeatherDto(
	UUID id,
	LocalDateTime forecastedAt,
	LocalDateTime forecastAt,
	WeatherAPILocation location,
	String skyStatus,
	PrecipitationDto precipitation,
	HumidityDto humidity,
	TemperatureDto temperature,
	WindSpeedDto windSpeed
) {
}