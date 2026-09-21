package com.codeit.otboo.api.recommendation.ranking;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;

public enum ClothesRole {
    TOP,
    BOTTOM,
    ONE_PIECE,
    OUTER,
    SHOES,
    OPTIONAL;

    public static ClothesRole from(ClothesCategory category) {
        return switch (category) {
            case TOP -> TOP;
            case PANTS, SKIRT -> BOTTOM;
            case DRESS -> ONE_PIECE;
            case OUTER -> OUTER;
            case SHOES -> SHOES;
            case HAT, BAG, ACCESSORY -> OPTIONAL;
        };
    }
}
