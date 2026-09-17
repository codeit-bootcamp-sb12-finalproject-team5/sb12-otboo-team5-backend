package com.codeit.otboo.api.outfit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OutfitCreateRequest(
    @NotBlank(message = "Outfit 이름 입력은 필수입니다.")
    @Size(max = 100, message = "Outfit 이름은 100자 이하여야 합니다.")
    String name,
    String description,
    @NotEmpty(message = "최소 한 개 이상의 의상을 선택해야 합니다.")
    List<@NotNull(message = "의상 ID는 null일 수 없습니다.") UUID> clothesIds
) {
}
