package com.codeit.otboo.api.outfit.dto;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import java.util.List;
import java.util.UUID;

public record OutfitDetailResponse(
    UUID id,
    String name,
    String description,
    List<ClothesImage> clothes
) {
    public static OutfitDetailResponse of(Outfit outfit, List<Clothes> clothes) {
        return new OutfitDetailResponse(
            outfit.getId(),
            outfit.getName(),
            outfit.getDescription(),
            clothes.stream().map(ClothesImage::of).toList()
        );
    }

    public record ClothesImage(UUID id, String imageUrl) {
        private static ClothesImage of(Clothes clothes) {
            return new ClothesImage(
                clothes.getId(),
                clothes.getImageUrl()
            );
        }
    }
}
