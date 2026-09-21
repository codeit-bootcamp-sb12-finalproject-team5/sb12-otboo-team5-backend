package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LlmRecommendationRequest(
    WeatherContext weather,
    List<LlmClothesCandidate> candidates
) {
    public record WeatherContext(
        BigDecimal currentTemperature,
        BigDecimal minTemperature,
        BigDecimal maxTemperature,
        SkyStatus skyStatus,
        PrecipitationType precipitationType
    ) {
    }

    public record LlmClothesCandidate(
        UUID id,
        String category,
        String originalCategory,
        String name,
        String color,
        String fit,
        List<String> styles,
        List<String> materials,
        String pattern,
        String season,
        double rankingScore
    ) {
    }
}
