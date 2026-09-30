package com.codeit.otboo.api.recommendation.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;

import java.util.UUID;

public record UserPreferenceSurveyOption(
        ClothesCategory category,
        UUID clothesId,
        String imageUrl
) {
}
