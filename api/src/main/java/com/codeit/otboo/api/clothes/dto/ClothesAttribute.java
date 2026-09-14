package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.*;

import java.util.List;

public record ClothesAttribute(
    String definitionId,
    String definitionName,
    List<String> selectableValues,
    String value
) {

    public String toEmbeddingText() {
        return switch (definitionId) {
            case "2" -> "카테고리:" + Displayable.from(ClothesSubCategory.class, value).getDisplayName();
            case "3" -> "색상:" + Displayable.from(ClothesColor.class, value).getDisplayName();
            case "4" -> "핏:" + Displayable.from(ClothesFit.class, value).getDisplayName();
            case "5" -> "소재:" + Displayable.from(ClothesMaterial.class, value).getDisplayName();
            case "6" -> "패턴:" + Displayable.from(ClothesPattern.class, value).getDisplayName();
            case "7" -> "스타일:" + Displayable.from(ClothesStyle.class, value).getDisplayName();
            default -> null;
        };
    }

}
