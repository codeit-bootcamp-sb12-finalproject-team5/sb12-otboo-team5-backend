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
    public ClothesAnalysisResult withImageUrl(String imageUrl) {
        return new ClothesAnalysisResult(
                name, brand, imageUrl, category, subcategory, color, fit,
                material, pattern, style, season, gender, description
        );
    }

    public ClothesAnalysisResult withoutBracketedNameAndBrand() {
        return new ClothesAnalysisResult(
                removeBracketedText(name), removeBracketedText(brand), imageUrl,
                category, subcategory, color, fit, material, pattern, style,
                season, gender, description
        );
    }

    private static String removeBracketedText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String normalized = value
                .replaceAll("\\[[^]]*]|\\([^)]*\\)|\\{[^}]*}|【[^】]*】", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank() ? value.trim() : normalized;
    }
}
