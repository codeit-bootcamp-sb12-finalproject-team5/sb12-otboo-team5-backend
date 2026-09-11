package com.codeit.otboo.batch.weather.writer;

import com.codeit.otboo.batch.weather.processor.NormalizedGridResult;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.fasterxml.uuid.Generators;
import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class WeatherJdbcItemWriter implements ItemWriter<NormalizedGridResult> {

    private static final int JDBC_BATCH_SIZE = 500;

    private static final String UPSERT_OBSERVATION_SQL = """
            INSERT INTO weather_observation
                (id, grid_id, observed_at, temperature, humidity, precipitation_type,
                 precipitation_amount, wind_speed, wind_direction, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (grid_id, observed_at) DO UPDATE SET
                temperature = EXCLUDED.temperature,
                humidity = EXCLUDED.humidity,
                precipitation_type = EXCLUDED.precipitation_type,
                precipitation_amount = EXCLUDED.precipitation_amount,
                wind_speed = EXCLUDED.wind_speed,
                wind_direction = EXCLUDED.wind_direction,
                updated_at = now()
            """;

    private static final String UPSERT_FORECAST_SQL = """
            INSERT INTO weather_forecast
                (id, grid_id, forecasted_at, forecast_at, temperature, humidity, precipitation_type,
                 precipitation_amount, precipitation_probability, sky_status, wind_speed, wind_direction,
                 min_temperature, max_temperature, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            ON CONFLICT (grid_id, forecast_at) DO UPDATE SET
                forecasted_at = EXCLUDED.forecasted_at,
                temperature = EXCLUDED.temperature,
                humidity = EXCLUDED.humidity,
                precipitation_type = EXCLUDED.precipitation_type,
                precipitation_amount = EXCLUDED.precipitation_amount,
                precipitation_probability = EXCLUDED.precipitation_probability,
                sky_status = EXCLUDED.sky_status,
                wind_speed = EXCLUDED.wind_speed,
                wind_direction = EXCLUDED.wind_direction,
                min_temperature = EXCLUDED.min_temperature,
                max_temperature = EXCLUDED.max_temperature,
                updated_at = now()
            WHERE weather_forecast.forecasted_at <= EXCLUDED.forecasted_at
            """;

    private final JdbcTemplate jdbcTemplate;

    private final AtomicInteger writtenGridCount = new AtomicInteger();
    private final AtomicInteger partialGridCount = new AtomicInteger();

    @Override
    @Transactional
    public void write(Chunk<? extends NormalizedGridResult> chunk) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BatchException(ErrorCode.BATCH_TRANSACTION_REQUIRED);
        }
        List<WeatherObservation> observations = new ArrayList<>();
        List<WeatherForecast> forecasts = new ArrayList<>();

        for (NormalizedGridResult item : chunk) {
            observations.addAll(item.observations());
            forecasts.addAll(item.forecasts());
        }

        batchUpsertObservations(observations);
        batchUpsertForecasts(forecasts);

        int gridCount = chunk.size();
        int partialCount = (int) chunk.getItems().stream().filter(NormalizedGridResult::partial).count();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writtenGridCount.addAndGet(gridCount);
                partialGridCount.addAndGet(partialCount);
                log.info("[BATCH][WRITER] chunk 커밋 완료 격자={}, 관측행={}, 예보행={}",
                    gridCount, observations.size(), forecasts.size());
            }
        });
    }

    private void batchUpsertObservations(List<WeatherObservation> observations) {
        forEachBatch(observations, slice ->
                jdbcTemplate.batchUpdate(UPSERT_OBSERVATION_SQL, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        WeatherObservation observation = slice.get(i);
                        ps.setObject(1, Generators.timeBasedEpochGenerator().generate());
                        ps.setObject(2, observation.getGrid().getId());
                        ps.setObject(3, observation.getObservedAt());
                        ps.setBigDecimal(4, observation.getTemperature());
                        ps.setBigDecimal(5, observation.getHumidity());
                        ps.setString(6, observation.getPrecipitationType());
                        ps.setBigDecimal(7, observation.getPrecipitationAmount());
                        ps.setBigDecimal(8, observation.getWindSpeed());
                        ps.setBigDecimal(9, observation.getWindDirection());
                    }

                    @Override
                    public int getBatchSize() {
                        return slice.size();
                    }
                }));
    }

    private void batchUpsertForecasts(List<WeatherForecast> forecasts) {
        forEachBatch(forecasts, slice ->
                jdbcTemplate.batchUpdate(UPSERT_FORECAST_SQL, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        WeatherForecast forecast = slice.get(i);
                        ps.setObject(1, Generators.timeBasedEpochGenerator().generate());
                        ps.setObject(2, forecast.getGrid().getId());
                        ps.setObject(3, forecast.getForecastedAt());
                        ps.setObject(4, forecast.getForecastAt());
                        ps.setBigDecimal(5, forecast.getTemperature());
                        ps.setBigDecimal(6, forecast.getHumidity());
                        ps.setString(7, forecast.getPrecipitationType());
                        ps.setBigDecimal(8, forecast.getPrecipitationAmount());
                        ps.setBigDecimal(9, forecast.getPrecipitationProbability());
                        ps.setString(10, forecast.getSkyStatus());
                        ps.setBigDecimal(11, forecast.getWindSpeed());
                        ps.setBigDecimal(12, forecast.getWindDirection());
                        ps.setBigDecimal(13, forecast.getMinTemperature());
                        ps.setBigDecimal(14, forecast.getMaxTemperature());
                    }

                    @Override
                    public int getBatchSize() {
                        return slice.size();
                    }
                }));
    }

    private <T> void forEachBatch(List<T> items, Consumer<List<T>> action) {
        for (int start = 0; start < items.size(); start += JDBC_BATCH_SIZE) {
            action.accept(items.subList(start, Math.min(start + JDBC_BATCH_SIZE, items.size())));
        }
    }

    public int getWrittenGridCount() {
        return writtenGridCount.get();
    }

    public int getPartialGridCount() {
        return partialGridCount.get();
    }
}
