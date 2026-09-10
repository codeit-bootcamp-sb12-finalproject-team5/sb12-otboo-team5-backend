package com.codeit.otboo.batch.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.batch.weather.tasklet.WeatherCleanupTasklet;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class WeatherCleanupTaskletTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2026-02-30", "2026-09-10T03:00:00+09:00"})
    void rejectsInvalidDateBeforeDeletion(String value) {
        assertThatThrownBy(() -> WeatherCleanupTasklet.parseCleanupDate(value))
            .isInstanceOfSatisfying(BatchException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_BATCH_CLEANUP_DATE));
    }
}
