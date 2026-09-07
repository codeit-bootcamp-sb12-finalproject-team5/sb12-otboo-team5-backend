package com.codeit.otboo.api.weather.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record LocationReadRequest(
        @NotNull
        @DecimalMin("-180")
        @DecimalMax("180")
        Double longitude,
        @NotNull
        @DecimalMin(value = "-90", inclusive = false)
        @DecimalMax(value = "90", inclusive = false)
        Double latitude
) {
}
