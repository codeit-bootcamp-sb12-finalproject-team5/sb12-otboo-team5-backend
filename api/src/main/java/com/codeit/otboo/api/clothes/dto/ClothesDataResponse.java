package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ClothesDataResponse(
    UUID id,
    UUID ownerId,
    String name,
    String imageUrl,
    String type,
    List<ClothesAttribute> attributes
) {

    public static ClothesDataResponse of(
        ClothesAnalysisResult result
    ) {
        List<ClothesAttribute> attributes = new ArrayList<>();

        addEnumAttribute(attributes, "의상성별", result.gender());
        addEnumAttribute(attributes, "소분류", result.subcategory());
        addEnumAttribute(attributes, "색상", result.color());
        addEnumAttribute(attributes, "핏", result.fit());
        addEnumAttribute(attributes, "소재", result.material());
        addEnumAttribute(attributes, "패턴", result.pattern());
        addEnumAttribute(attributes, "스타일", result.style());
        addEnumAttribute(attributes, "계절감", result.season());

        if (result.brand() != null && !result.brand().isBlank()) {
            attributes.add(new ClothesAttribute(
                null,
                "브랜드",
                List.of(),
                result.brand()
            ));
        }

        return new ClothesDataResponse(
            null,
            null,
            result.name(),
            result.imageUrl(),
            result.category().getDisplayName(),
            attributes
        );
    }
    private static <E extends Enum<E> & Displayable> void addEnumAttribute(
        List<ClothesAttribute> list,
        String definitionName,
        E enumValue
    ) {
        if (enumValue != null) {
            List<String> selectableValues = Arrays.stream(enumValue.getDeclaringClass().getEnumConstants())
                .map(Displayable::getDisplayName)
                .toList();
            list.add(new ClothesAttribute(
                null,
                definitionName,
                selectableValues,
                enumValue.getDisplayName()
            ));
        }
    }
}
