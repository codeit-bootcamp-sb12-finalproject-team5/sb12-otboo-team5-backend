package com.codeit.otboo.api.outfit.dto;

import java.util.UUID;

public record OutfitImageResponse(
        UUID outfitId,
        String imageUrl
) {
}
