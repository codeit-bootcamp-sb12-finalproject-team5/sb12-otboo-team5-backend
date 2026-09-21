package com.codeit.otboo.api.recommendation.history;

import java.util.Map;
import java.util.UUID;

/** 랭킹 다양성 계산에 필요한 최근 추천 노출 집계값입니다. */
public record RecommendationHistoryContext(
    Map<UUID, Integer> recent3ExposureCount,
    Map<UUID, Integer> olderExposureCount
) {
    public RecommendationHistoryContext {
        recent3ExposureCount = Map.copyOf(recent3ExposureCount);
        olderExposureCount = Map.copyOf(olderExposureCount);
    }

    public static RecommendationHistoryContext empty() {
        return new RecommendationHistoryContext(Map.of(), Map.of());
    }
}
