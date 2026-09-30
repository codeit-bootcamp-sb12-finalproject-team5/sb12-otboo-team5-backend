package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ClothesSearchRequest(
    @Nullable
    String cursor,
    @Nullable
    UUID idAfter,
    @NotNull
    Integer limit,
    @Nullable
    ClothesCategory typeEqual,
    @NotNull
    UUID ownerId
) {

}
