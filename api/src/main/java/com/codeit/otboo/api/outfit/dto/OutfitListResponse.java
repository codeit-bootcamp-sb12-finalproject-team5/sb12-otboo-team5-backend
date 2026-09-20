package com.codeit.otboo.api.outfit.dto;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.outfit.entity.Outfit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OutfitListResponse(
    UUID id,
    String name,
    String description,
    List<ClothesSummary> clothes,
    OffsetDateTime createdAt
) {
    public static OutfitListResponse of(Outfit outfit, List<Clothes> clothes) {
        return new OutfitListResponse(
            outfit.getId(),
            outfit.getName(),
            outfit.getDescription(),
            clothes.stream().map(ClothesSummary::of).toList(),
            outfit.getCreatedAt()
        );
    }

    public record ClothesSummary(UUID id, String name, String imageUrl) {
        private static ClothesSummary of(Clothes clothes) {
            return new ClothesSummary(
                clothes.getId(),
                clothes.getName(),
                clothes.getImageUrl()
            );
        }
    }
}
