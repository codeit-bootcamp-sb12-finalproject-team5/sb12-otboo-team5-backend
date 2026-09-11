package com.codeit.otboo.api.profile.dto.request;

import com.codeit.otboo.domain.profile.entity.Gender;
import com.codeit.otboo.domain.profile.entity.LocationSource;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record ProfileUpdateRequest(
    String name,
    Gender gender,
    LocalDate birthDate,

    @DecimalMin("-180")
    @DecimalMax("180")
    Double longitude,
    @DecimalMin(value = "-90", inclusive = false)
    @DecimalMax(value = "90", inclusive = false)
    Double latitude,

    LocationSource locationSource,

    @Min(1)
    @Max(5)
    Short temperatureSensitivity
) {

}
