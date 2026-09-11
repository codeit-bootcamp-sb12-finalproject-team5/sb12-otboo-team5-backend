package com.codeit.otboo.batch.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.batch.weather.config.WeatherCollectionWindow;
import com.codeit.otboo.batch.weather.processor.WeatherDataCountException;
import com.codeit.otboo.batch.weather.writer.WeatherJdbcItemWriter;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.item.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;

class BatchExceptionTest {
    @Test
    void missingWeatherKeepsSkipExceptionTypeAndGridDetails() {
        UUID gridId = UUID.randomUUID();
        var exception = new WeatherDataCountException(gridId, 60, 127);
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BATCH_WEATHER_DATA_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.BATCH_WEATHER_DATA_UNAVAILABLE.getMessage());
        assertThat(exception.getDetails()).containsEntry("gridId", gridId)
            .containsEntry("nx", 60).containsEntry("ny", 127);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "2026-09-10T23:50:00"})
    void invalidCollectionTimeUsesCommonErrorCode(String value) {
        assertThatThrownBy(() -> WeatherCollectionWindow.collectionAt(value))
            .isInstanceOfSatisfying(BatchException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_BATCH_COLLECTION_TIME));
    }

    @Test
    void missingTransactionFailsWithCommonErrorCode() {
        var writer = new WeatherJdbcItemWriter(mock(JdbcTemplate.class));
        assertThatThrownBy(() -> writer.write(new Chunk<>()))
            .isInstanceOfSatisfying(BatchException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BATCH_TRANSACTION_REQUIRED));
    }
}
