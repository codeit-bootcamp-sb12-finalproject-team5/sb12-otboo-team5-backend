package com.codeit.otboo.api.recommendation.dto;

import com.codeit.otboo.api.recommendation.history.RecommendationDailyLimitPolicy;

public record RecommendationUsageResponse(Usage ootd, Usage outfit) {
    public static RecommendationUsageResponse of(
        RecommendationDailyLimitPolicy.Usage ootd,
        RecommendationDailyLimitPolicy.Usage outfit
    ) {
        return new RecommendationUsageResponse(Usage.of(ootd), Usage.of(outfit));
    }

    public record Usage(int limit, int used, int remaining) {
        private static Usage of(RecommendationDailyLimitPolicy.Usage usage) {
            return new Usage(usage.limit(), usage.used(), usage.remaining());
        }
    }
}
