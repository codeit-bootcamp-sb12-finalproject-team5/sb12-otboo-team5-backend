package com.codeit.otboo.api.recommendation.preference;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import java.util.UUID;

public record ClothesContributionSnapshot(
    UUID clothesId,
    Integer preference,
    float[] attributeVector
) {

    public ClothesContributionSnapshot {
        attributeVector = attributeVector == null ? null : attributeVector.clone();
    }

    public static ClothesContributionSnapshot from(Clothes clothes) {
        return new ClothesContributionSnapshot(
            clothes.getId(),
            clothes.getPreference(),
            clothes.getAttributeVector()
        );
    }

    @Override
    public float[] attributeVector() {
        return attributeVector == null ? null : attributeVector.clone();
    }
}
