package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.Displayable;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

public record ClothesAttributeResponse(
    String id,
    String name,
    List<String> selectableValues,
    OffsetDateTime createdAt
) {
    public static <E extends Enum<E> & Displayable> ClothesAttributeResponse of(Class<E> enumClass) {
        String definitionName = getDefinitionName(enumClass);
        String definitionId = getDefinitionId(enumClass);

        List<String> selectableValues = Arrays.stream(enumClass.getEnumConstants())
            .map(Displayable::getDisplayName)
            .toList();

        return new ClothesAttributeResponse(
            definitionId,
            definitionName,
            selectableValues,
            null
        );
    }
    private static String getDefinitionName(Class<?> enumClass) {
        return switch (enumClass.getSimpleName()) {
            case "ClothesSubCategory" -> "소분류";
            case "ClothesColor" -> "색상";
            case "ClothesFit" -> "핏";
            case "ClothesMaterial" -> "소재";
            case "ClothesPattern" -> "패턴";
            case "ClothesStyle" -> "스타일";
            case "ClothesSeason" -> "계절감";
            case "ClothesGender" -> "의상성별";
            default -> "기타 속성";
        };
    }
    private static String getDefinitionId(Class<?> enumClass) {
        return switch (enumClass.getSimpleName()) {
            case "ClothesSubCategory" -> "2";
            case "ClothesColor" -> "3";
            case "ClothesFit" -> "4";
            case "ClothesMaterial" -> "5";
            case "ClothesPattern" -> "6";
            case "ClothesStyle" -> "7";
            case "ClothesSeason" -> "8";
            case "ClothesGender" -> "9";
            default -> "0";
        };
    }
}
