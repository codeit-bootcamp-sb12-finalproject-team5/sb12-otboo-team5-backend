package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.api.recommendation.ranking.RankedClothes;
import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.api.recommendation.ranking.ClothesRole;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

// 랭킹된 후보와 날씨를 Gemini에 보낼 최소 DTO로 바꾸는 로직
@Component
public class LlmRecommendationRequestMapper {

    public LlmRecommendationRequest map(WeatherForecast weather, RankedClothesCandidates rankedCandidates) {
        List<LlmRecommendationRequest.LlmClothesCandidate> selectedClothes = rankedCandidates.selectedClothes().stream()
            .map(this::mapSelectedClothes)
            .toList();
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
            selectedClothes,
            candidates
        );
    }

    /** 고정 선택 의상은 rankingScore 없이 후보와 동일한 정보 구조로 변환한다. */
    private LlmRecommendationRequest.LlmClothesCandidate mapSelectedClothes(Clothes clothes) {
        return mapClothes(clothes, null);
    }

    private LlmRecommendationRequest.LlmClothesCandidate mapCandidate(RankedClothes ranked) {
        return mapClothes(ranked.clothes(), ranked.finalScore());
    }

    /** 원래 카테고리와 논리 역할을 함께 Gemini 입력으로 구성한다. */
    private LlmRecommendationRequest.LlmClothesCandidate mapClothes(Clothes clothes, Double rankingScore) {
        Map<String, String> attributes = parseAttributes(clothes.getAttributeText());

        return new LlmRecommendationRequest.LlmClothesCandidate(
            clothes.getId(),
            clothes.getCategory().name(),
            ClothesRole.from(clothes.getCategory()).name(),
            clothes.getName(),
            attributes.get("색상"),
            attributes.get("핏"),
            values(attributes.get("스타일")),
            values(attributes.get("소재")),
            attributes.get("패턴"),
            clothes.getSeason() == null ? null : clothes.getSeason().name(), rankingScore
        );
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
