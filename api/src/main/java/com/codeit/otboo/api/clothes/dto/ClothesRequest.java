package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record ClothesRequest (
    @NotNull(message = "사용자 ID 입력은 필수입니다.")
    UUID ownerId,
    @NotEmpty(message = "의상 이름 입력은 필수입니다.")
    String name,
    @Nullable
    String brand,
    @NotNull
    ClothesCategory type,
    @Nullable
    ClothesSeason season,
    @Nullable
    ClothesGender gender,
    @Nullable
    List<ClothesAttribute> attributes,
    @Nullable
    String description,
    @NotNull
    Boolean isOwned,
    @Nullable
    @Range(min = 1, max = 5, message = "1에서 5 사이의 값이어야 합니다.")
    Integer preference
) implements ClothesAttributeProvider {

    public static ClothesRequest of(UUID userId, ClothesAnalysisResult result) {
        List<ClothesAttribute> attributes = new ArrayList<>();
        addEnumAttribute(attributes, "2", "소분류", result.subcategory());
        addEnumAttribute(attributes, "3", "색상", result.color());
        addEnumAttribute(attributes, "4", "핏", result.fit());
        addEnumAttribute(attributes, "5", "소재", result.material());
        addEnumAttribute(attributes, "6", "패턴", result.pattern());
        addEnumAttribute(attributes, "7", "스타일", result.style());
        return new ClothesRequest(
            userId,
            result.name(),
            result.brand(),
            result.category(),
            result.season(),
            result.gender(),
            attributes,
            result.description(),
            true,
            3
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
}
