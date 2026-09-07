package com.codeit.otboo.api.weather.dto.response;

import java.math.BigDecimal;

public record HumidityDto(
    BigDecimal current,
    BigDecimal comparedToDayBefore
) {
}
