package com.codeit.otboo.api.recommendation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record UserPreferenceRequest(
    @NotEmpty @Size(max = 45) List<@NotNull UUID> clothesIds
) {
}
