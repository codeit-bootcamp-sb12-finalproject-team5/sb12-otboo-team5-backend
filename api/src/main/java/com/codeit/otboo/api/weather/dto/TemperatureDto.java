package com.codeit.otboo.api.weather.dto;

import java.math.BigDecimal;

public record TemperatureDto(
	BigDecimal current,
	BigDecimal comparedToDayBefore,
	BigDecimal min,
	BigDecimal max
) {
}