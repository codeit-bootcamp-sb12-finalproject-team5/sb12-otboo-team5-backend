package com.codeit.otboo.api.outfit.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OutfitUpdateRequest(
    @Pattern(regexp = ".*\\S.*", message = "Outfit 이름은 빈 값일 수 없습니다.")
    @Size(max = 100, message = "Outfit 이름은 100자 이하여야 합니다.")
    String name,
    String description,
    @Size(max = 100, message = "Outfit 카테고리는 100자 이하여야 합니다.")
    String category,
    List<UUID> clothesIds
) {
}
