package com.codeit.otboo.api.weather.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class WeatherRequestValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void acceptsValidCoordinatesInBothRequests() {
        for (double longitude : new double[] {-180, 126.978, 180}) {
            assertThat(validator.validate(new LocationReadRequest(longitude, 37.5665))).isEmpty();
            assertThat(validator.validate(new WeatherReadRequest(longitude, 37.5665))).isEmpty();
        }
    }

    @Test
    void rejectsMissingOutOfRangeAndNonFiniteCoordinatesInBothRequests() {
        Double[][] invalid = {{null, 37.5}, {127.0, null}, {181.0, 37.5}, {-181.0, 37.5},
                {127.0, 90.0}, {127.0, -90.0}, {Double.NaN, 37.5}, {127.0, Double.NaN},
                {Double.POSITIVE_INFINITY, 37.5}, {127.0, Double.NEGATIVE_INFINITY}};
        for (Double[] coordinate : invalid) {
            assertThat(validator.validate(new LocationReadRequest(coordinate[0], coordinate[1])))
                    .isNotEmpty();
            assertThat(validator.validate(new WeatherReadRequest(coordinate[0], coordinate[1])))
                    .isNotEmpty();
        }
    }
}
