package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.api.recommendation.history.RecommendationHistoryContext;
import com.codeit.otboo.api.recommendation.history.RecommendationHistoryPolicy;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class HistoryPenaltyPolicy {
    public double calculatePenalty(UUID clothesId, RecommendationHistoryContext context, Set<UUID> selectedClothesIds) {
        if (selectedClothesIds.contains(clothesId)) return 0;

        return context.recent3ExposureCount().getOrDefault(clothesId, 0) * RecommendationHistoryPolicy.STRONG_HISTORY_PENALTY
            + context.olderExposureCount().getOrDefault(clothesId, 0) * RecommendationHistoryPolicy.WEAK_HISTORY_PENALTY;
    }
}
