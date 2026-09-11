package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

    public static ClothesResponse of(Clothes clothes, UUID userId) {
        List<ClothesAttribute> attributes = new ArrayList<>();
        String text = clothes.getAttributeText();
        String extractedBrand = null;
        if (text != null && !text.isBlank()) {
            String[] tokens = text.split("\n");
            for(String token : tokens) {
                String[] pair = token.split(":", 2);
                if (pair.length == 2) {
                    String key = pair[0].trim();
                    String value = pair[1].trim();
                    switch(key) {
                        case "카테고리": addEnumAttribute(attributes, "2", "소분류", Displayable.from(ClothesSubCategory.class, value)); break;
                        case "색상": addEnumAttribute(attributes, "3", "색상", Displayable.from(ClothesColor.class, value)); break;
                        case "핏": addEnumAttribute(attributes, "4", "핏", Displayable.from(ClothesFit.class, value)); break;
                        case "소재": addEnumAttribute(attributes, "5", "소재", Displayable.from(ClothesMaterial.class, value)); break;
                        case "패턴": addEnumAttribute(attributes, "6", "패턴", Displayable.from(ClothesPattern.class, value)); break;
                        case "스타일": addEnumAttribute(attributes, "7", "스타일", Displayable.from(ClothesStyle.class, value)); break;
                        case "계절감": addEnumAttribute(attributes, "8", "계절감", Displayable.from(ClothesSeason.class, value)); break;
                        case "브랜드": extractedBrand = value; break;
                    }
                } else {
                    throw new ClothesException(ErrorCode.CLOTHES_ATTRIBUTE_PARSE_FAILED);
                }
            }
        }
        addEnumAttribute(attributes, "9", "의상성별", clothes.getGender());
        return new ClothesResponse(
            clothes.getId(),
            userId,
            clothes.getName(),
            extractedBrand,
            clothes.getImageUrl(),
            clothes.getCategory(),
            attributes,
            clothes.getIsOwned(),
            clothes.getPreference()
        );
    }
}
