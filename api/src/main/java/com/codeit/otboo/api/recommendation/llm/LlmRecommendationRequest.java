package com.codeit.otboo.api.recommendation.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LlmRecommendationRequest(
    WeatherContext weather,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<LlmClothesCandidate> selectedClothes,
    List<LlmClothesCandidate> candidates
) {
    public LlmRecommendationRequest(WeatherContext weather, List<LlmClothesCandidate> candidates) {
        this(weather, List.of(), candidates);
    }

    public record WeatherContext(
        BigDecimal currentTemperature,
        BigDecimal minTemperature,
        BigDecimal maxTemperature,
        String skyStatus,
        String precipitationType
    ) {
    }

    public record LlmClothesCandidate(
        UUID id,
        String category,
        String role,
        String name,
        String color,
        String fit,
        List<String> styles,
        List<String> materials,
        String pattern,
        String season,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double rankingScore
    ) {
    }
}
