package com.codeit.otboo.api.recommendation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import jakarta.validation.Validation;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserPreferenceRequestTest {
    @Test
    void validatesSelectedClothesIds() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new UserPreferenceRequest(List.of(UUID.randomUUID())))).isEmpty();
            assertThat(validator.validate(new UserPreferenceRequest(null))).isNotEmpty();
            assertThat(validator.validate(new UserPreferenceRequest(List.of()))).isNotEmpty();
            assertThat(validator.validate(new UserPreferenceRequest(Arrays.asList((UUID) null)))).isNotEmpty();
        }
    }
}