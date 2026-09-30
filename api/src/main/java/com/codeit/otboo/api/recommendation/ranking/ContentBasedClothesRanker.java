package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.api.recommendation.history.RecommendationHistoryContext;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentBasedClothesRanker {

    private final RecommendationRankingProperties rankingProperties;
    private final HistoryPenaltyPolicy historyPenaltyPolicy;


    /** 기존 점수에 최근 추천 이력 감점만 추가해 카테고리별 후보를 선정한다. */
    public RankedClothesCandidates rank(
        Profile profile,
        RecommendationType recommendationType,
        List<Clothes> filteredClothes,
        List<Clothes> selectedClothes,
        RecommendationHistoryContext historyContext
    ) {
        List<Clothes> rankingTargets = excludeSelectedRoles(filteredClothes, selectedClothes);
        if (rankingTargets.isEmpty()) {
            log.info(
                "[recommendation][ranking] 필터를 통과한 후보가 없어 랭킹 생략 type={}, userId={}",
                recommendationType,
                profile.getUser().getId()
            );
            return RankedClothesCandidates.of(selectedClothes, Map.of());
        }

        float[] preferenceVector = profile.getPreferenceVector();
        if (!isValidVector(preferenceVector)) {
            log.warn(
                "[recommendation][ranking] 사용자 선호 벡터 유효하지 않음 type={}, userId={}",
                recommendationType,
                profile.getUser().getId()
            );
            return rankWithoutPreferenceVector(recommendationType, rankingTargets, selectedClothes, historyContext);
        }

        log.info(
            "[recommendation][ranking] 랭킹 시작 type={}, userId={}, inputCount={}, "
                + "preferenceVectorValid={}",
            recommendationType,
            profile.getUser().getId(),
            rankingTargets.size(),
            isValidVector(preferenceVector)
        );

        List<Clothes> validVectorClothes = rankingTargets.stream()
            .filter(clothes -> hasValidAttributeVector(clothes))
            .toList();
        List<RankedClothes> rankedClothes = validVectorClothes.stream()
            .map(clothes -> rankWithPreferenceVector(
                recommendationType, clothes, preferenceVector, historyContext, selectedClothes))
            .toList();

        log.info(
            "[recommendation][ranking] 후보 점수 계산 완료 type={}, inputCount={}, "
                + "validVectorCount={}, invalidVectorCount={}",
            recommendationType,
            rankingTargets.size(),
            validVectorClothes.size(),
            rankingTargets.size() - validVectorClothes.size()
        );

        RankedClothesCandidates result = selectTopK(selectedClothes, rankedClothes);
        logRankingResult(recommendationType, result);
        return result;
    }

    // 선호벡터 없는 경우
    private RankedClothesCandidates rankWithoutPreferenceVector(
        RecommendationType recommendationType,
        List<Clothes> filteredClothes,
        List<Clothes> selectedClothes,
        RecommendationHistoryContext historyContext
    ) {
        if (recommendationType == RecommendationType.OUTFIT) {
            throw ProfileException.preferenceVectorNotReady();
        }

        List<RankedClothes> rankedClothes = filteredClothes.stream()
            .map(clothes -> rankByExplicitPreference(clothes, historyContext, selectedClothes))
            .toList();

        log.info(
            "[recommendation][ranking] 옷에 설정된 선호도로 대체 랭킹 수행 "
                + "type={}, inputCount={}, scoredCount={}",
            recommendationType,
            filteredClothes.size(),
            rankedClothes.size()
        );

        RankedClothesCandidates result = selectTopK(selectedClothes, rankedClothes);
        logRankingResult(recommendationType, result);
        return result;
    }

    private RankedClothes rankWithPreferenceVector(
        RecommendationType recommendationType,
        Clothes clothes,
        float[] preferenceVector,
        RecommendationHistoryContext historyContext,
        List<Clothes> selectedClothes
    ) {
        double similarity = cosineSimilarity(preferenceVector, clothes.getAttributeVector());
        if (recommendationType == RecommendationType.OUTFIT) {
            return new RankedClothes(clothes, similarity, null,
                applyHistoryPenalty(clothes, similarity, historyContext, selectedClothes));
        }

        double normalizedPreference = normalizedPreference(clothes);
        double finalScore = similarity * SIMILARITY_WEIGHT + normalizedPreference * PREFERENCE_WEIGHT;

        return new RankedClothes(clothes, similarity, normalizedPreference,
            applyHistoryPenalty(clothes, finalScore, historyContext, selectedClothes));
    }

    private RankedClothes rankByExplicitPreference(
        Clothes clothes,
        RecommendationHistoryContext historyContext,
        List<Clothes> selectedClothes
    ) {
        double normalizedPreference = normalizedPreference(clothes);
        return new RankedClothes(clothes, null, normalizedPreference,
            applyHistoryPenalty(clothes, normalizedPreference, historyContext, selectedClothes));
    }

    private double applyHistoryPenalty(
        Clothes clothes,
        double baseScore,
        RecommendationHistoryContext historyContext,
        List<Clothes> selectedClothes
    ) {
        Set<UUID> selectedClothesIds = selectedClothes.stream()
            .map(Clothes::getId)
            .collect(java.util.stream.Collectors.toSet());

        return baseScore - historyPenaltyPolicy.calculatePenalty(clothes.getId(), historyContext, selectedClothesIds);
    }

    private RankedClothesCandidates selectTopK(List<Clothes> selectedClothes, List<RankedClothes> rankedClothes) {
        Comparator<RankedClothes> scoreOrder = Comparator
            .comparingDouble(RankedClothes::finalScore)
            .reversed()
            .thenComparing(ranked -> ranked.clothes().getId());

        Map<ClothesCategory, List<RankedClothes>> result = new EnumMap<>(ClothesCategory.class);
        rankingProperties.limits().forEach((category, limit) ->
            result.put(category, topK(rankedClothes, category, limit, scoreOrder))
        );

        return RankedClothesCandidates.of(selectedClothes, result);
    }

    /** 선택 의상이 이미 채운 역할과 충돌하는 카테고리를 추가 랭킹에서 제거한다. */
    private List<Clothes> excludeSelectedRoles(List<Clothes> candidates, List<Clothes> selectedClothes) {
        Set<ClothesRole> selectedRoles = selectedClothes.stream()
            .map(clothes -> ClothesRole.from(clothes.getCategory()))
            .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(ClothesRole.class)));

        return candidates.stream()
            .filter(candidate -> isRankingTarget(candidate.getCategory(), selectedRoles))
            .toList();
    }

    private boolean isRankingTarget(ClothesCategory category, java.util.Set<ClothesRole> selectedRoles) {
        ClothesRole role = ClothesRole.from(category);

        if (selectedRoles.contains(ClothesRole.ONE_PIECE)) {
            return role != ClothesRole.TOP && role != ClothesRole.BOTTOM && role != ClothesRole.ONE_PIECE;
        }

        return !selectedRoles.contains(role);
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
