package com.codeit.otboo.api.recommendation.temperature;

import java.math.BigDecimal;

/**
 * 현재 기온에 사용자 온도 민감도를 반영하는 정책입니다.
 */
public interface TemperatureAdjustmentPolicy {

    BigDecimal calculateEffectiveTemperature(BigDecimal currentTemperature, short temperatureSensitivity);
}
