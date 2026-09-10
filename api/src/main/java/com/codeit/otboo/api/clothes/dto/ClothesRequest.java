package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.codeit.otboo.domain.clothes.enums.Displayable;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.Range;

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
    List<ClothesAttribute> attributes,
    @NotNull
    Boolean isOwned,
    @Nullable
    @Range(min = 1, max = 5, message = "1에서 5 사이의 값이어야 합니다.")
    Integer preference
) {

    public String toEmbeddingText() {
        if (attributes == null || attributes.isEmpty()) {
            return "";
        }
        return attributes.stream()
            .map(ClothesAttribute::toEmbeddingText)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" "));
    }

    public ClothesCategory getCategory() {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        return attributes.stream()
            .filter(e -> e.definitionId().equals("1"))
            .map(e -> Displayable.from(ClothesCategory.class, e.value()))
            .findFirst()
            .orElseThrow(() ->
                new ClothesException(
                    ErrorCode.INVALID_ATTRIBUTE_VALUE
                )
            );
    }

    public ClothesSubCategory getSubCategory() {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        return attributes.stream()
            .filter(e -> e.definitionId().equals("1"))
            .map(e -> Displayable.from(ClothesSubCategory.class, e.value()))
            .findFirst()
            .orElseThrow(() ->
                new ClothesException(
                    ErrorCode.INVALID_ATTRIBUTE_VALUE
                )
            );
    }

    public ClothesGender getGender() {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        return attributes.stream()
            .filter(e -> e.definitionId().equals("1"))
            .map(e -> Displayable.from(ClothesGender.class, e.value()))
            .findFirst()
            .orElseThrow(() ->
                new ClothesException(
                    ErrorCode.INVALID_ATTRIBUTE_VALUE
                )
            );
    }

}
