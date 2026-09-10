package com.codeit.otboo.batch.weather.tasklet;

import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class WeatherCleanupTasklet implements Tasklet {
    private final JdbcTemplate jdbcTemplate;
    private final LocalDate cleanupDate;

    public WeatherCleanupTasklet(JdbcTemplate jdbcTemplate,
            @Value("#{jobParameters['cleanupDate']}") String cleanupDate) {
        this.jdbcTemplate = jdbcTemplate;
        this.cleanupDate = parseCleanupDate(cleanupDate);
    }

    public static LocalDate parseCleanupDate(String value) {
        if (value == null || value.isBlank()) {
            throw new BatchException(ErrorCode.INVALID_BATCH_CLEANUP_DATE);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BatchException(ErrorCode.INVALID_BATCH_CLEANUP_DATE, exception);
        }
    }

    /** Step의 동일 트랜잭션 안에서 두 테이블을 정리합니다. */
    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        OffsetDateTime dayStart = cleanupDate.atStartOfDay().atOffset(KmaTimeCalculator.KST);
        OffsetDateTime observationCutoff = dayStart.minusDays(2);
        OffsetDateTime forecastCutoff = dayStart.minusDays(1);

        int observations = jdbcTemplate.update(
            "DELETE FROM weather_observation WHERE observed_at < ?", observationCutoff);
        int forecasts = jdbcTemplate.update(
            "DELETE FROM weather_forecast WHERE forecast_at < ?", forecastCutoff);

        var executionContext = contribution.getStepExecution().getExecutionContext();
        executionContext.putString("observationCutoff", observationCutoff.toString());
        executionContext.putString("forecastCutoff", forecastCutoff.toString());
        executionContext.putInt("deletedObservationCount", observations);
        executionContext.putInt("deletedForecastCount", forecasts);
        log.info("[BATCH][CLEANUP] 삭제 처리 cleanupDate={}, observationCutoff={}, observations={}, forecastCutoff={}, forecasts={}",
            cleanupDate, observationCutoff, observations, forecastCutoff, forecasts);
        return RepeatStatus.FINISHED;
    }
}
