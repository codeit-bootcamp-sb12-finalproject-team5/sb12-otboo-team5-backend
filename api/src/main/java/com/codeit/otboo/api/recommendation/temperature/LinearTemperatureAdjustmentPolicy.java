package com.codeit.otboo.api.recommendation.temperature;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class LinearTemperatureAdjustmentPolicy implements TemperatureAdjustmentPolicy {

    private static final BigDecimal ADJUSTMENT_PER_SENSITIVITY = new BigDecimal("0.6");

    @Override
    public BigDecimal calculateEffectiveTemperature(BigDecimal currentTemperature, short temperatureSensitivity) {
        BigDecimal adjustment = BigDecimal.valueOf(temperatureSensitivity)
            .multiply(ADJUSTMENT_PER_SENSITIVITY);

        return currentTemperature.add(adjustment);
    }
}
