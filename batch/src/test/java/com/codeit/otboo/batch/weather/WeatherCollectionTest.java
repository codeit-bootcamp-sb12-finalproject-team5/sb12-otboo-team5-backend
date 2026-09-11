package com.codeit.otboo.batch.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.batch.weather.reader.WeatherGridItemReader;
import com.codeit.otboo.batch.weather.config.WeatherCollectionWindow;
import com.codeit.otboo.batch.weather.config.WeatherSyncStepListener;
import com.codeit.otboo.batch.weather.service.WeatherBatchExecutionService;
import com.codeit.otboo.batch.weather.writer.WeatherJdbcItemWriter;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.BatchStatus;

class WeatherCollectionTest {
    private static final String BASE = "2026-09-08T23:50:00+09:00";

    @Test
    void storedUtcObservationsSkipAllEightCallsAndForecastUsesFixedBase() {
        var grids = mock(WeatherGridRepository.class);
        var observations = mock(WeatherObservationRepository.class);
        var kma = mock(KmaClient.class);
        var grid = WeatherGrid.builder().id(UUID.randomUUID()).nx(60).ny(127).build();
        var slots = WeatherCollectionWindow.observationSlots(OffsetDateTime.parse(BASE));
        when(grids.findByEnabledTrueOrderByIdAsc()).thenReturn(List.of(grid));
        when(observations.findByGrid_IdAndObservedAtIn(grid.getId(), slots)).thenReturn(slots.stream()
            .<WeatherObservation>map(slot -> WeatherObservation.builder().observedAt(slot.withOffsetSameInstant(ZoneOffset.UTC)).build()).toList());
        var forecastAt = OffsetDateTime.parse("2026-09-08T23:00:00+09:00");
        when(kma.findVillageForecast(forecastAt, 60, 127))
            .thenReturn(Optional.of(new KmaForecastBundleDto(forecastAt, 60, 127, List.of())));

        var reader = new WeatherGridItemReader(grids, observations, kma, BASE);
        var result = reader.read();

        assertThat(result.existingObservationCount()).isEqualTo(8);
        assertThat(result.fetchedObservations()).isEmpty();
        assertThat(reader.read()).isNull();
        verify(kma, never()).findObservation(any(), anyInt(), anyInt());
        verify(kma).findVillageForecast(forecastAt, 60, 127);
    }

    @Test
    void endUsesSameDateAndGridCountAsStartEvenOnLaterDay() {
        var executionService = mock(WeatherBatchExecutionService.class);
        var grids = mock(WeatherGridRepository.class);
        var writer = mock(WeatherJdbcItemWriter.class);
        when(grids.countByEnabledTrue()).thenReturn(10L, 20L);
        when(writer.getWrittenGridCount()).thenReturn(10);
        var listener = new WeatherSyncStepListener(executionService, grids, writer);
        var parameters = new JobParametersBuilder().addString("collectionAt", BASE).toJobParameters();
        var step = new StepExecution("weather", new JobExecution(1L, parameters));
        listener.beforeStep(step);
        step.setStatus(BatchStatus.COMPLETED);
        listener.afterStep(step);

        var date = OffsetDateTime.parse(BASE).toLocalDate();
        verify(executionService).start(date, 10);
        verify(executionService).finish(date, "COMPLETED", 10, 0, null);
        verify(grids, times(1)).countByEnabledTrue();
    }
}
