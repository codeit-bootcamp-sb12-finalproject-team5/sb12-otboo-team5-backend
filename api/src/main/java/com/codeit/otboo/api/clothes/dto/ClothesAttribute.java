package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.codeit.otboo.domain.clothes.enums.Displayable;
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
            case "8" -> "계절감:" + Displayable.from(ClothesSeason.class, value).getDisplayName();
            default -> null;
        };
    }

}
