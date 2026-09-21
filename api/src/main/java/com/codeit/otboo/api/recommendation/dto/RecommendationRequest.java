package com.codeit.otboo.api.recommendation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RecommendationRequest(
    @NotNull UUID weatherId,
    List<UUID> selectedClothesIds
) {
    public boolean hasSelectedClothes() {
        return selectedClothesIds != null && !selectedClothesIds.isEmpty();
    }
}
