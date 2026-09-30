package com.codeit.otboo.api.weather.dto.response;

import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import java.math.BigDecimal;

public record PrecipitationDto(
    PrecipitationType type,
    BigDecimal amount,
    BigDecimal probability
) {
}
