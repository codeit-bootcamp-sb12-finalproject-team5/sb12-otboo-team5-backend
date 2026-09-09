package com.codeit.otboo.api.clothes.dto;

import com.codeit.otboo.domain.clothes.enums.ClothingCategory;
import java.util.UUID;

public record ClothingDataResponse(
    UUID id,
    UUID ownerId,
    String name,
    String imageUrl,
    ClothingCategory type,
    ClothingAttribute[] attributes
) {
}
