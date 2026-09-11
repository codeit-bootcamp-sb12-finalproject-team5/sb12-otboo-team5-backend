package com.codeit.otboo.batch.weather.reader;

import com.codeit.otboo.batch.weather.config.WeatherCollectionWindow;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class WeatherGridItemReader implements ItemReader<GridCollectionResult> {

    private final WeatherGridRepository gridRepository;
    private final WeatherObservationRepository observationRepository;
    private final KmaClient kmaClient;

    private final OffsetDateTime collectionAt;
    private Iterator<WeatherGrid> gridIterator;

    public WeatherGridItemReader(WeatherGridRepository gridRepository,
            WeatherObservationRepository observationRepository, KmaClient kmaClient,
            @Value("#{stepExecutionContext['collectionAt']}") String collectionAt) {
        this.gridRepository = gridRepository;
        this.observationRepository = observationRepository;
        this.kmaClient = kmaClient;
        this.collectionAt = WeatherCollectionWindow.collectionAt(collectionAt);
    }

    @Override
    public GridCollectionResult read() {
        if (gridIterator == null) {
            List<WeatherGrid> grids = gridRepository.findByEnabledTrueOrderByIdAsc();
            log.info("[BATCH][READER] 활성 격자 {}개", grids.size());
            gridIterator = grids.iterator();
        }

        if (!gridIterator.hasNext()) {
            return null;
        }

        return collect(gridIterator.next());
    }

    private GridCollectionResult collect(WeatherGrid grid) {
        List<OffsetDateTime> slots = WeatherCollectionWindow.observationSlots(collectionAt);

        Set<Instant> existingSlots = observationRepository
                .findByGrid_IdAndObservedAtIn(grid.getId(), slots).stream()
                .map(observation -> observation.getObservedAt().toInstant())
                .collect(Collectors.toSet());

        List<KmaObservationDto> fetchedObservations = new ArrayList<>();
        for (OffsetDateTime slot : slots) {
            if (existingSlots.contains(slot.toInstant())) continue;
            kmaClient.findObservation(slot, grid.getNx(), grid.getNy())
                    .filter(observation -> observation.observedAt().isEqual(slot))
                    .ifPresent(fetchedObservations::add);
        }

        var forecastBundle = collectForecast(grid);

        log.info("[BATCH][READER] gridId={} nx={} ny={} 기존관측={} 신규관측={} 예보번들={}",
                grid.getId(), grid.getNx(), grid.getNy(),
                existingSlots.size(), fetchedObservations.size(), forecastBundle.isPresent());

        return new GridCollectionResult(grid, existingSlots.size(), fetchedObservations, forecastBundle);
    }
    private Optional<KmaForecastBundleDto> collectForecast(WeatherGrid grid) {
        OffsetDateTime base = KmaTimeCalculator.villageBase(collectionAt);
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = kmaClient.findVillageForecast(base.minusHours(attempt * 3L), grid.getNx(), grid.getNy());
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }
}
