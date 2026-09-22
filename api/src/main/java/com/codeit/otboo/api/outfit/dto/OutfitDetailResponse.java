package com.codeit.otboo.api.outfit.dto;

import com.codeit.otboo.api.feed.dto.FeedResponse;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.outfit.entity.Ootd;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public record OutfitDetailResponse(
    UUID id,
    String name,
    String description,
    String category,
    List<ClothesImage> clothes,
    FeedResponse.OotdWeatherResponse weather
) {
    public static OutfitDetailResponse of(Outfit outfit, List<Clothes> clothes, Ootd ootd) {
        return of(outfit, clothes, ootd, Function.identity());
    }

    public static OutfitDetailResponse of(
        Outfit outfit,
        List<Clothes> clothes,
        Ootd ootd,
        Function<String, String> imageUrlResolver
    ) {
        FeedResponse.OotdWeatherResponse weather = ootd == null ? null
                : FeedResponse.OotdWeatherResponse.of(ootd);
        return new OutfitDetailResponse(
            outfit.getId(),
            outfit.getName(),
            outfit.getDescription(),
            outfit.getCategory(),
            clothes.stream().map(clothesItem -> ClothesImage.of(
                clothesItem, imageUrlResolver.apply(clothesItem.getImageUrl())
            )).toList(),
            weather
        );
    }

    public record ClothesImage(UUID id, String imageUrl) {
        private static ClothesImage of(Clothes clothes, String imageUrl) {
            return new ClothesImage(
                clothes.getId(),
                imageUrl
            );
        }
    }
}
