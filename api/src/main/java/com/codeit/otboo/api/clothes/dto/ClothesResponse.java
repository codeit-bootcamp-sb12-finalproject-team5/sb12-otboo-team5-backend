package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ClothesResponse(
    UUID id,
    UUID ownerId,
    String name,
    String brand,
    String imageUrl,
    ClothesCategory type,
    List<ClothesAttribute> attributes,
    Boolean isOwned,
    Integer preference
) {
    public static ClothesResponse of(ClothesAnalysisResult result) {
        List<ClothesAttribute> attributes = new ArrayList<>();
        addEnumAttribute(attributes, "2", "소분류", result.subcategory());
        addEnumAttribute(attributes, "3", "색상", result.color());
        addEnumAttribute(attributes, "4", "핏", result.fit());
        addEnumAttribute(attributes, "5", "소재", result.material());
        addEnumAttribute(attributes, "6", "패턴", result.pattern());
        addEnumAttribute(attributes, "7", "스타일", result.style());
        addEnumAttribute(attributes, "8", "계절감", result.season());
        addEnumAttribute(attributes, "9", "의상성별", result.gender());

        return new ClothesResponse(
            null,
            null,
            result.brand(),
            result.name(),
            result.imageUrl(),
            result.category(),
            attributes,
            null,
            null
        );
    }
    private static <E extends Enum<E> & Displayable> void addEnumAttribute(
        List<ClothesAttribute> list,
        String definitionId,
        String definitionName,
        E enumValue
    ) {
        if (enumValue != null) {
            List<String> selectableValues = Arrays.stream(enumValue.getDeclaringClass().getEnumConstants())
                .map(Displayable::getDisplayName)
                .toList();
            list.add(new ClothesAttribute(
                definitionId,
                definitionName,
                selectableValues,
                enumValue.getDisplayName()
            ));
        }
    }

//    public static ClothesResponse of(Clothes clothes) {
//        List<ClothesAttribute> attributes = new ArrayList<>();
//        addEnumAttribute(attributes, "소분류", result.subcategory());
//        addEnumAttribute(attributes, "색상", result.color());
//        addEnumAttribute(attributes, "핏", result.fit());
//        addEnumAttribute(attributes, "소재", result.material());
//        addEnumAttribute(attributes, "패턴", result.pattern());
//        addEnumAttribute(attributes, "스타일", result.style());
//        addEnumAttribute(attributes, "계절감", result.season());
//        addEnumAttribute(attributes, "의상성별", result.gender());
//
//        return new ClothesResponse(
//            null,
//            null,
//            clothes.getName(),
//            clothes.getImageUrl(),
//            result.brand(),
//            attributes
//        );
//    }
}
