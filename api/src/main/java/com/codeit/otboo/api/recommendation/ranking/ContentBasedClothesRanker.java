package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentBasedClothesRanker {

    private final RecommendationRankingProperties rankingProperties;

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

        List<Clothes> validVectorClothes = filteredClothes.stream()
            .filter(clothes -> hasValidAttributeVector(clothes))
            .toList();
        List<RankedClothes> rankedClothes = validVectorClothes.stream()
            .map(clothes -> rankWithPreferenceVector(recommendationType, clothes, preferenceVector))
            .toList();

        log.info(
            "[recommendation][ranking] 후보 점수 계산 완료 type={}, inputCount={}, "
                + "validVectorCount={}, invalidVectorCount={}",
            recommendationType,
            filteredClothes.size(),
            validVectorClothes.size(),
            filteredClothes.size() - validVectorClothes.size()
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

        Map<ClothesCategory, List<RankedClothes>> result = new EnumMap<>(ClothesCategory.class);
        rankingProperties.limits().forEach((category, limit) ->
            result.put(category, topK(rankedClothes, category, limit, scoreOrder))
        );
        return new RankedClothesCandidates(result);
    }

    private List<RankedClothes> topK(
        List<RankedClothes> rankedClothes,
        ClothesCategory category,
        int limit,
        Comparator<RankedClothes> scoreOrder
    ) {
        return rankedClothes.stream()
            .filter(ranked -> ranked.clothes().getCategory() == category)
            .sorted(scoreOrder)
            .limit(limit)
            .toList();
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
            "[recommendation][ranking] 카테고리별 상위 후보 선정 완료 type={}, counts={}",
            recommendationType,
            result.byCategory().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                entry -> entry.getKey().name(), entry -> entry.getValue().size()
            ))
        );
        result.byCategory().forEach((category, candidates) -> logSelected(category.name(), candidates));
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
