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
            return RankedClothesCandidates.empty();
        }

        float[] preferenceVector = profile.getPreferenceVector();

        if (!isValidVector(preferenceVector)) {
            log.warn("[recommendation] preference vector is invalid. userId={}", profile.getUser().getId());
            return rankWithoutPreferenceVector(recommendationType, filteredClothes);
        }

        List<RankedClothes> rankedClothes = filteredClothes.stream()
            .filter(this::isIncludedCategory)
            .filter(clothes -> hasValidAttributeVector(clothes))
            .map(clothes -> rankWithPreferenceVector(recommendationType, clothes, preferenceVector))
            .toList();

        return selectTopK(rankedClothes);
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

        return selectTopK(rankedClothes);
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

        log.warn("[recommendation] attribute vector is invalid. clothesId={}", clothes.getId());
        return false;
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
