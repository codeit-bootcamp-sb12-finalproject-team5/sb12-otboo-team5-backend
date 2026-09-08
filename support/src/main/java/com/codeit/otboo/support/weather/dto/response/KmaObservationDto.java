package com.codeit.otboo.support.weather.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** 기상청 초단기실황 응답을 서비스 계층에 전달하는 DTO입니다. */
public record KmaObservationDto(
        LocalDateTime observedAt,
        int nx,
        int ny,
        Map<String, String> categories
) {
    /** 지정한 실황 항목을 숫자로 변환하고 변환할 수 없으면 기본값을 반환합니다. */
    public BigDecimal value(String category, BigDecimal fallback) {
        try {
            String value = categories.get(category);
            return value == null ? fallback : new BigDecimal(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
