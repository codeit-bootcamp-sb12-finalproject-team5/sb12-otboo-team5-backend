package com.codeit.otboo.api.outfit.dto;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OutfitCreateResponse(
    UUID id,
    String name,
    String description,
    String category,
    List<ClothesSummary> clothes,
    OffsetDateTime createdAt
) {
    public static OutfitCreateResponse of(Outfit outfit, List<Clothes> clothes) {
        return new OutfitCreateResponse(
            outfit.getId(),
            outfit.getName(),
            outfit.getDescription(),
            outfit.getCategory(),
            clothes.stream().map(ClothesSummary::of).toList(),
            outfit.getCreatedAt()
        );
    }

    public record ClothesSummary(
        UUID id,
        String name,
        String imageUrl
    ) {
        private static ClothesSummary of(Clothes clothes) {
            return new ClothesSummary(
                clothes.getId(),
                clothes.getName(),
                clothes.getImageUrl()
            );
        }
    }
}
