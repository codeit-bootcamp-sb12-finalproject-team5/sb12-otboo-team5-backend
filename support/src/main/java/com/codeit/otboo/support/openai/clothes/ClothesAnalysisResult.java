package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;

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
    ClothesGender gender
) {
}
