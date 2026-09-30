package com.codeit.otboo.api.recommendation.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(List<Outfit> outfits) {
    public record Outfit(
        int rank,
        List<Clothes> clothes,
        String reason,
        List<String> styleTags
    ) { }

    public record Clothes(
        UUID id,
        String name,
        String imageUrl,
        String category
    ) { }
}
