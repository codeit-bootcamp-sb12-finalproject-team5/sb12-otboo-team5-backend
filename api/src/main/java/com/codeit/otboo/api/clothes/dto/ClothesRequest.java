package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.Range;

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
}
