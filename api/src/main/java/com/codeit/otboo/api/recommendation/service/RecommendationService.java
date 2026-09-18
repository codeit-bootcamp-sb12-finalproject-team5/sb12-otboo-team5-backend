package com.codeit.otboo.api.recommendation.service;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.api.recommendation.dto.RecommendationResponse;
import com.codeit.otboo.api.recommendation.dto.UserPreferenceRequest;
import com.codeit.otboo.api.recommendation.llm.GeminiRecommendationResult;
import com.codeit.otboo.api.recommendation.llm.LlmRecommendationService;
import com.codeit.otboo.api.recommendation.ranking.RankedClothes;
import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.exception.WeatherException;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import com.codeit.otboo.support.storage.S3StorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final RecommendationRankingService rankingService;
    private final WeatherForecastRepository weatherForecastRepository;
    private final LlmRecommendationService llmRecommendationService;
    private final ProfileRepository profileRepository;
    private final ClothesAnalysisService clothesAnalysisService;
    private final S3StorageService s3StorageService;

    public RecommendationResponse recommendOotd(UUID userId, UUID weatherId) {
        return recommend(userId, weatherId, RecommendationType.OOTD);
    }

    public RecommendationResponse recommendOutfit(UUID userId, UUID weatherId) {
        return recommend(userId, weatherId, RecommendationType.OUTFIT);
    }

    private RecommendationResponse recommend(UUID userId, UUID weatherId, RecommendationType type) {
        log.info(
            "[recommendation][pipeline] 추천 처리를 시작 type={}, userId={}, weatherId={}",
            type,
            userId,
            weatherId
        );

        RankedClothesCandidates ranked = type == RecommendationType.OOTD
            ? rankingService.rankOotd(userId, weatherId) : rankingService.rankOutfit(userId, weatherId);

        log.info(
            "[recommendation][pipeline] 랭킹 처리가 완료 type={}, categoryCounts={}", type,
            ranked.byCategory().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().name(), entry -> entry.getValue().size()
            ))
        );

        if (!ranked.isRecommendable()) {
            log.warn(
                "[recommendation][pipeline] 기본 코디를 구성할 후보가 없어 추천을 중단 type={}, categoryCounts={}",
                type, ranked.byCategory()
            );
            return new RecommendationResponse(List.of());
        }

        WeatherForecast weather = weatherForecastRepository.findById(weatherId)
            .orElseThrow(() -> new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE));

        log.info(
            "[recommendation][llm] 코디 생성을 시작 type={}, candidateCount={}",
            type,
            totalCandidateCount(ranked)
        );
        GeminiRecommendationResult generated = llmRecommendationService.generate(weather, ranked);
        log.info(
            "[recommendation][llm] 코디 생성이 완료 type={}, outfitCount={}, "
                + "modelVersion={}, usage={}",
            type,
            generated.recommendation().outfits().size(),
            generated.modelVersion(),
            generated.usage()
        );

        Map<UUID, Clothes> candidates = ranked.all().map(RankedClothes::clothes)
            .collect(java.util.stream.Collectors.toMap(Clothes::getId, Function.identity()));

        RecommendationResponse response = new RecommendationResponse(
            generated.recommendation().outfits().stream().map(outfit ->
            new RecommendationResponse.Outfit(
                outfit.rank(),
                outfit.clothesIds().stream().map(candidates::get)
                .map(clothes -> new RecommendationResponse.Clothes(
                    clothes.getId(),
                    clothes.getName(),
                    s3StorageService.getPresignedUrl(clothes.getImageUrl()),
                    clothes.getCategory().name()))
                    .toList(),
                outfit.reason(),
                outfit.styleTags())
            ).toList()
        );

        log.info(
            "[recommendation][pipeline] 추천 처리가 완료 type={}, userId={}, outfitCount={}",
            type,
            userId,
            response.outfits().size()
        );
        return response;
    }

    @Transactional
    public void initializePreferenceVector(UUID userId, UserPreferenceRequest request) {
        log.info("[recommendation][preference] 선호 벡터 초기화를 시작 userId={}", userId);

        Profile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(ProfileException::profileNotFound);

        float[] preferenceVector = clothesAnalysisService.embed(request.toEmbeddingText());
        profile.updatePreferenceVector(preferenceVector);

        log.info(
            "[recommendation][preference] 선호 벡터 초기화가 완료 userId={}, vectorDimension={}",
            userId,
            preferenceVector == null ? null : preferenceVector.length
        );
    }

    private int totalCandidateCount(RankedClothesCandidates ranked) {
        return (int) ranked.all().count();
    }
}
