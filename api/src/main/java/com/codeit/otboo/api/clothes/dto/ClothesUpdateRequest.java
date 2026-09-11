package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hibernate.validator.constraints.Range;

public record ClothesUpdateRequest(
    @NotEmpty(message = "의상 이름 입력은 필수입니다.")
    String name,
    @Nullable
    String brand,
    @NotNull
    ClothesCategory type,
    @Nullable
    List<ClothesAttribute> attributes,
    @NotNull
    Boolean isOwned,
    @Nullable
    @Range(min = 1, max = 5, message = "1에서 5 사이의 값이어야 합니다.")
    Integer preference
) implements ClothesAttributeProvider {
}
