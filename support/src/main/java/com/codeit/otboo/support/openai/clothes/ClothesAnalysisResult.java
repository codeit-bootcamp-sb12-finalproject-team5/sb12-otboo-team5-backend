package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.enums.*;

public record ClothesAnalysisResult(
    String name,
    String brand,
    String imageUrl,
    ClothesCategory category,
    ClothesSubCategory subcategory,
    ClothesColor color,
    ClothesFit fit,
    ClothesMaterial material,
    ClothesPattern pattern,
    ClothesStyle style,
    ClothesSeason season,
    ClothesGender gender,
    String description
) {
}
