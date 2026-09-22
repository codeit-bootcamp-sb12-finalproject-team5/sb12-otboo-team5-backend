package com.codeit.otboo.api.recommendation.service;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.api.recommendation.dto.RecommendationResponse;
import com.codeit.otboo.api.recommendation.dto.RecommendationRequest;
import com.codeit.otboo.api.recommendation.dto.UserPreferenceRequest;
import com.codeit.otboo.api.recommendation.preference.PreferenceVectorAsyncService;
import com.codeit.otboo.api.recommendation.llm.GeminiRecommendationResult;
import com.codeit.otboo.api.recommendation.llm.LlmRecommendationService;
import com.codeit.otboo.api.recommendation.history.RecommendationDailyLimitPolicy;
import com.codeit.otboo.api.recommendation.history.RecommendationHistoryQueryService;
import com.codeit.otboo.api.recommendation.history.RecommendationHistorySaveService;
import com.codeit.otboo.api.recommendation.ranking.RankedClothes;
import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.exception.WeatherException;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {
    private static final Set<ClothesCategory> SUPPORTED_SELECTED_CLOTHES_CATEGORIES = EnumSet.of(
        ClothesCategory.TOP,
        ClothesCategory.PANTS,
        ClothesCategory.SKIRT,
        ClothesCategory.OUTER,
        ClothesCategory.DRESS,
        ClothesCategory.HAT,
        ClothesCategory.SHOES,
        ClothesCategory.BAG,
        ClothesCategory.ACCESSORY
    );

    private final RecommendationRankingService rankingService;
    private final WeatherForecastRepository weatherForecastRepository;
    private final LlmRecommendationService llmRecommendationService;
    private final PreferenceVectorAsyncService preferenceVectorInitializationAsyncService;
    private final S3StorageService s3StorageService;
    private final ClothesRepository clothesRepository;
    private final RecommendationDailyLimitPolicy recommendationDailyLimitPolicy;
    private final RecommendationHistoryQueryService recommendationHistoryQueryService;
    private final RecommendationHistorySaveService recommendationHistorySaveService;

    public RecommendationResponse recommendOotd(UUID userId, RecommendationRequest request) {
        return recommend(userId, request, RecommendationType.OOTD);
    }

    public RecommendationResponse recommendOutfit(UUID userId, RecommendationRequest request) {
        return recommend(userId, request, RecommendationType.OUTFIT);
    }

    private RecommendationResponse recommend(UUID userId, RecommendationRequest request, RecommendationType type) {
        // 추천 횟수 확인
        recommendationDailyLimitPolicy.validateAvailable(userId, type);

        // 선택 의상이 있는 경우
        if (request.hasSelectedClothes()) {
            List<Clothes> selectedClothes = validateSelectedClothes(userId, request.selectedClothesIds());
            return recommendWithSelectedClothes(userId, request.weatherId(), type, selectedClothes);
        }

        // 선택 의상 없는 경우
        return recommendNormally(userId, request.weatherId(), type);
    }

    private RecommendationResponse recommendWithSelectedClothes(
        UUID userId,
        UUID weatherId,
        RecommendationType type,
        List<Clothes> selectedClothes
    ) {
        log.info(
            "[recommendation][pipeline] 특정 옷 기반 추천 시작 type={}, userId={}, selectedClothesIds={}",
            type,
            userId,
            selectedClothes.stream().map(Clothes::getId).toList()
        );

        RankedClothesCandidates ranked = type == RecommendationType.OOTD
            ? rankingService.rankOotd(userId, weatherId, selectedClothes)
            : rankingService.rankOutfit(userId, weatherId, selectedClothes);

        return generateRecommendation(userId, weatherId, type, ranked);
    }

    private RecommendationResponse recommendNormally(UUID userId, UUID weatherId, RecommendationType type) {
        log.info(
            "[recommendation][pipeline] 추천 처리를 시작 type={}, userId={}, weatherId={}",
            type,
            userId,
            weatherId
        );

        RankedClothesCandidates ranked = type == RecommendationType.OOTD
            ? rankingService.rankOotd(userId, weatherId) : rankingService.rankOutfit(userId, weatherId);

        return generateRecommendation(userId, weatherId, type, ranked);
    }

    /**
     * 랭킹 결과를 기존 LLM 추천 생성 흐름에 전달한다.
     */
    private RecommendationResponse generateRecommendation(
        UUID userId,
        UUID weatherId,
        RecommendationType type,
        RankedClothesCandidates ranked
    ) {

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

        GeminiRecommendationResult generated = llmRecommendationService.generate(weather, ranked,
            recommendationHistoryQueryService.recentOutfitFingerprints(userId, type));

        log.info(
            "[recommendation][llm] 코디 생성이 완료 type={}, outfitCount={}, "
                + "modelVersion={}, usage={}",
            type,
            generated.recommendation().outfits().size(),
            generated.modelVersion(),
            generated.usage()
        );

        // Gemini 응답에는 고정 선택 의상도 포함되므로 응답 변환용 조회 맵에 함께 보관한다.
        Map<UUID, Clothes> candidates = Stream.concat(
                ranked.selectedClothes().stream(),
                ranked.all().map(RankedClothes::clothes)
            )
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

        recommendationHistorySaveService.save(
            userId,
            weatherId,
            type,
            LlmRecommendationService.PROMPT_VERSION,
            ranked.selectedClothes(),
            generated.recommendation()
        );

        log.info(
            "[recommendation][pipeline] 추천 처리가 완료 type={}, userId={}, outfitCount={}",
            type,
            userId,
            response.outfits().size()
        );

        return response;
    }

    /**
     * 선택 의상의 중복, 존재·삭제·소유 상태 및 허용 조합을 검증한다.
     */
    private List<Clothes> validateSelectedClothes(UUID userId, List<UUID> selectedClothesIds) {
        if (selectedClothesIds.stream().anyMatch(Objects::isNull)) {
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Set<UUID> uniqueClothesIds = Set.copyOf(selectedClothesIds);
        if (uniqueClothesIds.size() != selectedClothesIds.size()) {
            throw new ClothesException(ErrorCode.DUPLICATED_SELECTED_CLOTHES);
        }

        List<Clothes> selectedClothes = clothesRepository
            .findAllByIdInAndUser_IdAndDeletedAtIsNull(uniqueClothesIds, userId);
        if (selectedClothes.size() != uniqueClothesIds.size()) {
            validateMissingOrUnownedClothes(uniqueClothesIds);
        }

        validateSelectedClothesCombination(selectedClothes);
        return selectedClothes;
    }

    private void validateMissingOrUnownedClothes(Set<UUID> selectedClothesIds) {
        List<Clothes> existingClothes = clothesRepository
            .findAllByIdInAndDeletedAtIsNull(selectedClothesIds);

        if (existingClothes.size() != selectedClothesIds.size()) {
            throw new ClothesException(ErrorCode.SELECTED_CLOTHES_NOT_FOUND);
        }

        throw new ClothesException(ErrorCode.SELECTED_CLOTHES_NOT_OWNED);
    }

    /**
     * 카테고리별 한 벌 제한과 DRESS의 기본 의상 조합 규칙을 검증한다.
     */
    private void validateSelectedClothesCombination(List<Clothes> selectedClothes) {
        Map<ClothesCategory, Long> categoryCounts = selectedClothes.stream()
            .collect(java.util.stream.Collectors.groupingBy(Clothes::getCategory, java.util.stream.Collectors.counting()));

        if (!SUPPORTED_SELECTED_CLOTHES_CATEGORIES.containsAll(categoryCounts.keySet())
            || categoryCounts.values().stream().anyMatch(count -> count > 1)
            || (categoryCounts.containsKey(ClothesCategory.PANTS) && categoryCounts.containsKey(ClothesCategory.SKIRT))
            || (categoryCounts.containsKey(ClothesCategory.DRESS)
            && (categoryCounts.containsKey(ClothesCategory.TOP)
            || categoryCounts.containsKey(ClothesCategory.PANTS)
            || categoryCounts.containsKey(ClothesCategory.SKIRT)))) {
            throw new ClothesException(ErrorCode.INVALID_SELECTED_CLOTHES_COMBINATION);
        }
    }

    public void initializePreferenceVector(UUID userId, UserPreferenceRequest request) {
        preferenceVectorInitializationAsyncService.initialize(userId, request.toEmbeddingText());
    }

    private int totalCandidateCount(RankedClothesCandidates ranked) {
        return (int) ranked.all().count();
    }
}
