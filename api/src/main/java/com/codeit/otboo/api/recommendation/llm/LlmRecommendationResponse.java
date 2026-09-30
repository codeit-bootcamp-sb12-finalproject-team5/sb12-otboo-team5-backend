package com.codeit.otboo.api.recommendation.llm;

import java.util.List;
import java.util.UUID;

public record LlmRecommendationResponse(List<GeneratedOutfit> outfits) {
    public record GeneratedOutfit(
        int rank,
        List<UUID> clothesIds,
        String reason,
        List<String> styleTags
    ) {
    }
}
