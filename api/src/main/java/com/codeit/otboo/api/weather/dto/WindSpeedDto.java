package com.codeit.otboo.api.weather.dto;

import java.math.BigDecimal;

public record WindSpeedDto(
	BigDecimal speed,
	String asWord
) {
}
