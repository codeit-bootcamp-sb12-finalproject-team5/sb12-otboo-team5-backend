package com.codeit.otboo.domain.weather.dto;

import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import java.math.BigDecimal;

public record WeatherInfoResponse(
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    BigDecimal precipitationAmount,
    BigDecimal precipitationProbability,
    BigDecimal temperatureCurrent,
    BigDecimal temperatureComparedToDayBefore,
    BigDecimal temperatureMin,
    BigDecimal temperatureMax
) {
}
