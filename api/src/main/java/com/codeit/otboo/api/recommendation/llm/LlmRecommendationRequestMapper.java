package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.api.recommendation.ranking.RankedClothes;
import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

// 랭킹된 후보와 날씨를 Gemini에 보낼 최소 DTO로 바꾸는 로직
@Component
public class LlmRecommendationRequestMapper {

    public LlmRecommendationRequest map(WeatherForecast weather, RankedClothesCandidates rankedCandidates) {
        List<LlmRecommendationRequest.LlmClothesCandidate> candidates = rankedCandidates.all()
            .map(this::mapCandidate)
            .toList();

        return new LlmRecommendationRequest(
            new LlmRecommendationRequest.WeatherContext(
                weather.getTemperature(),
                weather.getMinTemperature(),
                weather.getMaxTemperature(),
                weather.getSkyStatus(),
                weather.getPrecipitationType()
            ),
            candidates
        );
    }

    private LlmRecommendationRequest.LlmClothesCandidate mapCandidate(RankedClothes ranked) {
        Clothes clothes = ranked.clothes();
        Map<String, String> attributes = parseAttributes(clothes.getAttributeText());

        return new LlmRecommendationRequest.LlmClothesCandidate(
            clothes.getId(),
            logicalCategory(clothes.getCategory()),
            clothes.getCategory().name(),
            clothes.getName(),
            attributes.get("색상"),
            attributes.get("핏"),
            values(attributes.get("스타일")),
            values(attributes.get("소재")),
            attributes.get("패턴"),
            clothes.getSeason() == null ? null : clothes.getSeason().name(), ranked.finalScore()
        );
    }

    private String logicalCategory(ClothesCategory category) {
        return category == ClothesCategory.PANTS || category == ClothesCategory.SKIRT ? "BOTTOM" : category.name();
    }

    private Map<String, String> parseAttributes(String attributeText) {
        if (attributeText == null || attributeText.isBlank()) return Map.of();

        return Arrays.stream(attributeText.split("\\R"))
            .map(line -> line.split(":", 2))
            .filter(pair -> pair.length == 2)
            .collect(java.util.stream.Collectors.toMap(pair -> pair[0].trim(), pair -> pair[1].trim(), (first, ignored) -> first));
    }

    private List<String> values(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }
}
