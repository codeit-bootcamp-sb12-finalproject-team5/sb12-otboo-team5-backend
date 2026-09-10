package com.codeit.otboo.batch.weather.processor;

import com.codeit.otboo.batch.weather.config.WeatherCollectionWindow;
import com.codeit.otboo.batch.weather.reader.GridCollectionResult;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.support.weather.normalize.KmaForecastNormalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Component
@StepScope
public class WeatherDataProcessor implements ItemProcessor<GridCollectionResult, NormalizedGridResult> {

    private final LocalDate collectionDate;

    public WeatherDataProcessor(@Value("#{stepExecutionContext['collectionAt']}") String collectionAt) {
        this.collectionDate = WeatherCollectionWindow.collectionAt(collectionAt).toLocalDate();
    }

    @Override
    public NormalizedGridResult process(GridCollectionResult item) {
        WeatherGrid grid = item.grid();

        List<WeatherObservation> normalizedObservations = item.fetchedObservations().stream()
                .map(observation -> KmaForecastNormalizer.normalizeObservation(grid, observation))
                .toList();
        int totalObservationCount = item.existingObservationCount() + normalizedObservations.size();

        List<WeatherForecast> normalizedForecasts = item.forecastBundle()
                .map(bundle -> KmaForecastNormalizer.normalizeForecasts(grid, bundle))
                .orElseGet(List::of);

        OffsetDateTime windowStart = WeatherCollectionWindow.forecastRangeStart(collectionDate);
        OffsetDateTime windowEnd = WeatherCollectionWindow.forecastRangeEnd(collectionDate);
        long futureForecastCount = normalizedForecasts.stream()
                .filter(forecast -> !forecast.getForecastAt().isBefore(windowStart)
                        && forecast.getForecastAt().isBefore(windowEnd))
                .count();

        if (totalObservationCount == 0 && normalizedForecasts.isEmpty()) {
            throw new WeatherDataCountException(grid.getId(), grid.getNx(), grid.getNy());
        }

        boolean partial = totalObservationCount < WeatherCollectionWindow.OBSERVATION_SLOT_COUNT
                || futureForecastCount < WeatherCollectionWindow.OBSERVATION_SLOT_COUNT;

        if (partial) {
            log.warn("[BATCH][PROCESSOR] 부분수집 gridId={} 관측={}/{} 미래예보={}/{}",
                    grid.getId(), totalObservationCount, WeatherCollectionWindow.OBSERVATION_SLOT_COUNT,
                    futureForecastCount, WeatherCollectionWindow.OBSERVATION_SLOT_COUNT);
        }

        return new NormalizedGridResult(grid, normalizedObservations, normalizedForecasts, partial);
    }
}
