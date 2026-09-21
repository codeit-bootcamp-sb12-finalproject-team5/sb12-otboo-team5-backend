package com.codeit.otboo.api.outfit.dto;

import com.codeit.otboo.api.feed.dto.FeedResponse;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.outfit.entity.Ootd;
import com.codeit.otboo.domain.outfit.entity.Outfit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public record OutfitListResponse(
    UUID id,
    String name,
    String description,
    String category,
    List<ClothesSummary> clothes,
    FeedResponse.OotdWeatherResponse weather,
    OffsetDateTime createdAt
) {
    public static OutfitListResponse of(Outfit outfit, List<Clothes> clothes, Ootd ootd) {
        return of(outfit, clothes, ootd, Function.identity());
    }

    public static OutfitListResponse of(
        Outfit outfit,
        List<Clothes> clothes,
        Ootd ootd,
        Function<String, String> imageUrlResolver
    ) {
        FeedResponse.OotdWeatherResponse weather = ootd == null ? null
                : FeedResponse.OotdWeatherResponse.of(ootd);
        return new OutfitListResponse(
            outfit.getId(),
            outfit.getName(),
            outfit.getDescription(),
            outfit.getCategory(),
            clothes.stream().map(clothesItem -> ClothesSummary.of(
                clothesItem, imageUrlResolver.apply(clothesItem.getImageUrl())
            )).toList(),
            weather,
            outfit.getCreatedAt()
        );
    }

    public record ClothesSummary(UUID id, String name, String imageUrl) {
        private static ClothesSummary of(Clothes clothes, String imageUrl) {
            return new ClothesSummary(
                clothes.getId(),
                clothes.getName(),
                imageUrl
            );
        }
    }
}
