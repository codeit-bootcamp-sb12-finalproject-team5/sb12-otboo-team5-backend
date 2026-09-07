package com.codeit.otboo.api.weather.dto.response;

import java.math.BigDecimal;

public record PrecipitationDto(
    String type,
    BigDecimal amount,
    BigDecimal probability
) {
}
