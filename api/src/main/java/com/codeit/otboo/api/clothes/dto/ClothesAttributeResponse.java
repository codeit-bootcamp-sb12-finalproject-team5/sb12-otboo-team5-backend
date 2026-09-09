package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.Displayable;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeResponse(
    UUID definitionId,
    String definitionName,
    List<String> selectableValues,
    OffsetDateTime createdAt
) {
    public static <E extends Enum<E> & Displayable> ClothesAttributeResponse of(Class<E> enumClass) {
        String definitionName = getDefinitionName(enumClass);

        List<String> selectableValues = Arrays.stream(enumClass.getEnumConstants())
            .map(Displayable::getDisplayName)
            .toList();

        return new ClothesAttributeResponse(
            null,
            definitionName,
            selectableValues,
            null
        );
    }
    private static String getDefinitionName(Class<?> enumClass) {
        return switch (enumClass.getSimpleName()) {
            case "ClothesGender" -> "성별";
            case "ClothesCategory" -> "대분류";
            case "ClothesSubCategory" -> "소분류";
            case "ClothesColor" -> "색상";
            case "ClothesFit" -> "핏";
            case "ClothesMaterial" -> "소재";
            case "ClothesPattern" -> "패턴";
            case "ClothesStyle" -> "스타일";
            case "ClothesSeason" -> "계절감";
            default -> "기타 속성";
        };
    }
}
