package com.codeit.otboo.api.feed.dto;

import com.codeit.otboo.api.clothes.dto.ClothesAttribute;
import com.codeit.otboo.api.clothes.dto.ClothesResponse;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;

import java.util.List;
import java.util.UUID;

public record ClothesSimpleResponse(
        UUID clothesId,
        String name,
        String imageUrl,
        ClothesCategory type,
        List<ClothesAttribute> attributes
) {
    public static ClothesSimpleResponse of(Clothes clothes, String imageUrl) {
        ClothesResponse response = ClothesResponse.of(
            clothes, clothes.getUser().getId(), imageUrl);
        return new ClothesSimpleResponse(
            clothes.getId(), clothes.getName(), imageUrl, clothes.getCategory(), response.attributes());
    }
}
