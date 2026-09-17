package com.codeit.otboo.api.recommendation.dto;

import com.codeit.otboo.domain.clothes.enums.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record UserPreferenceRequest(
        List<ClothesSubCategory> subcategories,
        List<ClothesColor> colors,
        List<ClothesFit> fits,
        List<ClothesMaterial> materials,
        List<ClothesPattern> patterns,
        List<ClothesStyle> styles
) {
    public String toEmbeddingText() {
        return String.join("\n",
                toAttributeText("카테고리", subcategories),
                toAttributeText("색상", colors),
                toAttributeText("핏", fits),
                toAttributeText("소재", materials),
                toAttributeText("패턴", patterns),
                toAttributeText("스타일", styles));
    }

    private static <T extends Displayable> String toAttributeText(String label, List<T> values) {
        String displayNames = values == null ? "" : values.stream()
                .filter(Objects::nonNull)
                .map(Displayable::getDisplayName)
                .collect(Collectors.joining(", "));
        return label + ":" + displayNames;
    }
}
