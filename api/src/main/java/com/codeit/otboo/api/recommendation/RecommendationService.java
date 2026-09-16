package com.codeit.otboo.api.recommendation;

import com.codeit.otboo.api.recommendation.dto.RecommendationResponse;
import com.codeit.otboo.api.recommendation.llm.GeminiRecommendationResult;
import com.codeit.otboo.api.recommendation.llm.LlmRecommendationService;
import com.codeit.otboo.api.recommendation.ranking.RankedClothes;
import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.exception.WeatherException;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRankingService rankingService;
    private final WeatherForecastRepository weatherForecastRepository;
    private final LlmRecommendationService llmRecommendationService;

    public RecommendationResponse recommendOotd(UUID userId, UUID weatherId) {
        return recommend(userId, weatherId, RecommendationType.OOTD);
    }

    public RecommendationResponse recommendOutfit(UUID userId, UUID weatherId) {
        return recommend(userId, weatherId, RecommendationType.OUTFIT);
    }

    private RecommendationResponse recommend(UUID userId, UUID weatherId, RecommendationType type) {
        RankedClothesCandidates ranked = type == RecommendationType.OOTD
            ? rankingService.rankOotd(userId, weatherId) : rankingService.rankOutfit(userId, weatherId);

        if (ranked.tops().isEmpty() || ranked.bottoms().isEmpty()) return new RecommendationResponse(List.of());

        WeatherForecast weather = weatherForecastRepository.findById(weatherId)
            .orElseThrow(() -> new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE));

        GeminiRecommendationResult generated = llmRecommendationService.generate(weather, ranked);
        Map<UUID, Clothes> candidates = Stream.of(ranked.tops(), ranked.bottoms(), ranked.outers(), ranked.shoes())
            .flatMap(List::stream).map(RankedClothes::clothes)
            .collect(java.util.stream.Collectors.toMap(Clothes::getId, Function.identity()));

        return new RecommendationResponse(generated.recommendation().outfits().stream().map(outfit ->
            new RecommendationResponse.Outfit(
                outfit.rank(),
                outfit.clothesIds().stream().map(candidates::get)
                .map(clothes -> new RecommendationResponse.Clothes(
                    clothes.getId(),
                    clothes.getName(),
                    clothes.getImageUrl(),
                    clothes.getCategory().name()))
                    .toList(),
                outfit.reason(),
                outfit.styleTags())
            ).toList()
        );
    }
}
