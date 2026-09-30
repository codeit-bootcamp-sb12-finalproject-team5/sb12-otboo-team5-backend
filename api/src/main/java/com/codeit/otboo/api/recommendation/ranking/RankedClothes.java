package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.domain.clothes.entity.Clothes;

/** Content-based ranking 중 계산된 의상별 점수입니다. */
public record RankedClothes(
    Clothes clothes,
    Double vectorSimilarity,
    Double normalizedPreference,
    double finalScore
) {
}
