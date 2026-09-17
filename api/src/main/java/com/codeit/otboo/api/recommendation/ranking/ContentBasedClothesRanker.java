package com.codeit.otboo.api.recommendation.ranking;

import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.BOTTOM_LIMIT;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.NEUTRAL_PREFERENCE;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.OUTER_LIMIT;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.PREFERENCE_MAX;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.PREFERENCE_WEIGHT;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.SHOES_LIMIT;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.SIMILARITY_WEIGHT;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.TOP_LIMIT;
import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.VECTOR_DIMENSION;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentBasedClothesRanker {

    private final ProfileRepository profileRepository;

    public RankedClothesCandidates rank(
        UUID userId,
        RecommendationType recommendationType,
        List<Clothes> filteredClothes
    ) {
        if (filteredClothes.isEmpty()) {
            return RankedClothesCandidates.empty();
        }

        Profile profile = profileRepository.findByUser_Id(userId)
            .orElseThrow(ProfileException::profileNotFound);
        return rank(profile, recommendationType, filteredClothes);
    }

    public RankedClothesCandidates rank(
        Profile profile,
        RecommendationType recommendationType,
        List<Clothes> filteredClothes
    ) {
        if (filteredClothes.isEmpty()) {
            log.info(
                "[recommendation][ranking] 필터를 통과한 후보가 없어 랭킹 생략 type={}, userId={}",
                recommendationType,
                profile.getUser().getId()
            );
            return RankedClothesCandidates.empty();
        }

        float[] preferenceVector = profile.getPreferenceVector();

        log.info(
            "[recommendation][ranking] 랭킹 시작 type={}, userId={}, inputCount={}, "
                + "preferenceVectorValid={}",
            recommendationType,
            profile.getUser().getId(),
            filteredClothes.size(),
            isValidVector(preferenceVector)
        );

        if (!isValidVector(preferenceVector)) {
            log.warn(
                "[recommendation][ranking] 사용자 선호 벡터 유효하지 않음 type={}, userId={}",
                recommendationType,
                profile.getUser().getId()
            );
            return rankWithoutPreferenceVector(recommendationType, filteredClothes);
        }

        List<Clothes> includedCategories = filteredClothes.stream()
            .filter(this::isIncludedCategory)
            .toList();
        List<Clothes> validVectorClothes = includedCategories.stream()
            .filter(clothes -> hasValidAttributeVector(clothes))
            .toList();
        List<RankedClothes> rankedClothes = validVectorClothes.stream()
            .map(clothes -> rankWithPreferenceVector(recommendationType, clothes, preferenceVector))
            .toList();

        log.info(
            "[recommendation][ranking] 후보 점수 계산 완료 type={}, inputCount={}, "
                + "includedCategoryCount={}, excludedCategoryCount={}, validVectorCount={}, invalidVectorCount={}",
            recommendationType,
            filteredClothes.size(),
            includedCategories.size(),
            filteredClothes.size() - includedCategories.size(),
            validVectorClothes.size(),
            includedCategories.size() - validVectorClothes.size()
        );

        RankedClothesCandidates result = selectTopK(rankedClothes);
        logRankingResult(recommendationType, result);
        return result;
    }

    // 선호벡터 없는 경우
    private RankedClothesCandidates rankWithoutPreferenceVector(
        RecommendationType recommendationType,
        List<Clothes> filteredClothes
    ) {
        if (recommendationType == RecommendationType.OUTFIT) {
            throw ProfileException.preferenceVectorNotReady();
        }

        List<RankedClothes> rankedClothes = filteredClothes.stream()
            .filter(this::isIncludedCategory)
            .map(this::rankByExplicitPreference)
            .toList();

        log.info(
            "[recommendation][ranking] 옷에 설정된 선호도로 대체 랭킹 수행 "
                + "type={}, inputCount={}, scoredCount={}",
            recommendationType,
            filteredClothes.size(),
            rankedClothes.size()
        );

        RankedClothesCandidates result = selectTopK(rankedClothes);
        logRankingResult(recommendationType, result);
        return result;
    }

    private RankedClothes rankWithPreferenceVector(
        RecommendationType recommendationType,
        Clothes clothes,
        float[] preferenceVector
    ) {
        double similarity = cosineSimilarity(preferenceVector, clothes.getAttributeVector());
        if (recommendationType == RecommendationType.OUTFIT) {
            return new RankedClothes(clothes, similarity, null, similarity);
        }

        double normalizedPreference = normalizedPreference(clothes);
        double finalScore = similarity * SIMILARITY_WEIGHT + normalizedPreference * PREFERENCE_WEIGHT;

        return new RankedClothes(clothes, similarity, normalizedPreference, finalScore);
    }

    private RankedClothes rankByExplicitPreference(Clothes clothes) {
        double normalizedPreference = normalizedPreference(clothes);
        return new RankedClothes(clothes, null, normalizedPreference, normalizedPreference);
    }

    private RankedClothesCandidates selectTopK(List<RankedClothes> rankedClothes) {
        Comparator<RankedClothes> scoreOrder = Comparator
            .comparingDouble(RankedClothes::finalScore)
            .reversed()
            .thenComparing(ranked -> ranked.clothes().getId());

        return new RankedClothesCandidates(
            topK(rankedClothes, ClothesCategory.TOP, TOP_LIMIT, scoreOrder),
            topK(rankedClothes, null, BOTTOM_LIMIT, scoreOrder),
            topK(rankedClothes, ClothesCategory.OUTER, OUTER_LIMIT, scoreOrder),
            topK(rankedClothes, ClothesCategory.SHOES, SHOES_LIMIT, scoreOrder)
        );
    }

    private List<RankedClothes> topK(
        List<RankedClothes> rankedClothes,
        ClothesCategory category,
        int limit,
        Comparator<RankedClothes> scoreOrder
    ) {
        return rankedClothes.stream()
            .filter(ranked -> belongsToGroup(ranked.clothes().getCategory(), category))
            .sorted(scoreOrder)
            .limit(limit)
            .toList();
    }

    private boolean isIncludedCategory(Clothes clothes) {
        return belongsToGroup(clothes.getCategory(), ClothesCategory.TOP)
            || belongsToGroup(clothes.getCategory(), null)  // BOTTOM
            || belongsToGroup(clothes.getCategory(), ClothesCategory.OUTER)
            || belongsToGroup(clothes.getCategory(), ClothesCategory.SHOES);
    }

    private boolean belongsToGroup(ClothesCategory clothesCategory, ClothesCategory category) {
        if (category == null) {
            return clothesCategory == ClothesCategory.PANTS || clothesCategory == ClothesCategory.SKIRT;
        }

        return clothesCategory == category;
    }

    private boolean hasValidAttributeVector(Clothes clothes) {
        if (isValidVector(clothes.getAttributeVector())) {
            return true;
        }

        log.warn("[recommendation][ranking] 옷 속성 벡터 유효하지 않음 clothesId={}", clothes.getId());
        return false;
    }

    private void logRankingResult(
        RecommendationType recommendationType,
        RankedClothesCandidates result
    ) {
        log.info(
            "[recommendation][ranking] 카테고리별 상위 후보 선정 완료 "
                + "type={}, topCount={}, bottomCount={}, outerCount={}, shoesCount={}",
            recommendationType,
            result.tops().size(),
            result.bottoms().size(),
            result.outers().size(),
            result.shoes().size()
        );
        logSelected("TOP", result.tops());
        logSelected("BOTTOM", result.bottoms());
        logSelected("OUTER", result.outers());
        logSelected("SHOES", result.shoes());
    }

    private void logSelected(String group, List<RankedClothes> selected) {
        log.info(
            "[recommendation][ranking] 선정된 후보 group={}, candidates={}",
            group,
            selected.stream().map(this::summarizeScore).toList()
        );
    }

    private String summarizeScore(RankedClothes ranked) {
        return String.format(
            Locale.ROOT,
            "{id=%s, category=%s, finalScore=%.4f, similarity=%s, preference=%s}",
            ranked.clothes().getId(),
            ranked.clothes().getCategory(),
            ranked.finalScore(),
            formatNullableScore(ranked.vectorSimilarity()),
            formatNullableScore(ranked.normalizedPreference())
        );
    }

    private String formatNullableScore(Double score) {
        return score == null ? "N/A" : String.format(Locale.ROOT, "%.4f", score);
    }

    private boolean isValidVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            return false;
        }

        double squaredNorm = 0;
        for (Float value : vector) {
            if (value == null || !Float.isFinite(value)) {
                return false;
            }
            squaredNorm += value * value;
        }

        return squaredNorm > 0;
    }

    private double cosineSimilarity(float[] first, float[] second) {
        double dotProduct = 0;
        double firstSquaredNorm = 0;
        double secondSquaredNorm = 0;

        for (int index = 0; index < VECTOR_DIMENSION; index++) {
            double firstValue = first[index];
            double secondValue = second[index];

            dotProduct += firstValue * secondValue;
            firstSquaredNorm += firstValue * firstValue;
            secondSquaredNorm += secondValue * secondValue;
        }

        return dotProduct / (Math.sqrt(firstSquaredNorm) * Math.sqrt(secondSquaredNorm));
    }

    // 사용자의 옷 선호도 체크
    private double normalizedPreference(Clothes clothes) {
        Integer preference = clothes.getPreference();

        return preference == null ? NEUTRAL_PREFERENCE : preference / PREFERENCE_MAX;
    }
}
