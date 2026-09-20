package com.codeit.otboo.api.outfit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record OutfitUpdateRequest(
    @Pattern(regexp = ".*\\S.*", message = "Outfit 이름은 빈 값일 수 없습니다.")
    @Size(max = 100, message = "Outfit 이름은 100자 이하여야 합니다.")
    String name,
    String description,
    List<@NotNull(message = "의상 ID는 null일 수 없습니다.") UUID> clothesIds,
    @NotBlank(message = "Outfit 카테고리 입력은 필수입니다.")
    @Size(max = 100, message = "Outfit 카테고리는 100자 이하여야 합니다.")
    String category
) {
}
