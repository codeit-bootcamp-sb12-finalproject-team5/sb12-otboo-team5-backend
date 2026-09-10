package com.codeit.otboo.api.weather.repository;

import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class WeatherRepository {
    private final WeatherGridRepository gridRepository;
    private final WeatherObservationRepository observationRepository;
    private final WeatherForecastRepository forecastRepository;

    @Transactional(readOnly = true)
    public Optional<WeatherGrid> findGrid(int nx, int ny) {
        return gridRepository.findByNxAndNy(nx, ny);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WeatherGrid findOrCreateGrid(int nx, int ny, List<String> names) {
        Optional<WeatherGrid> existing = findGrid(nx, ny);
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            return gridRepository.saveAndFlush(WeatherGrid.create(nx, ny, names));
        } catch (DataIntegrityViolationException exception) {
            return findGrid(nx, ny).orElseThrow(() -> exception);
        }
    }

    @Transactional
    public void upsertObservations(List<WeatherObservation> observations) {
        if (observations.isEmpty()) return;

        Map<UUID, List<WeatherObservation>> grouped = observations.stream()
                .collect(Collectors.groupingBy(observation -> observation.getGrid().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        for (var group : grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            gridRepository.findByIdForUpdate(group.getKey()).orElseThrow();
            List<OffsetDateTime> observedTimes = group.getValue().stream()
                    .map(WeatherObservation::getObservedAt)
                    .toList();
            Map<Instant, WeatherObservation> existingByTime = observationRepository
                    .findByGrid_IdAndObservedAtIn(group.getKey(), observedTimes).stream()
                    .collect(Collectors.toMap(
                            observation -> observation.getObservedAt().toInstant(), observation -> observation));
            List<WeatherObservation> newObservations = new ArrayList<>();

            for (WeatherObservation incoming : group.getValue()) {
                WeatherObservation existing = existingByTime.get(incoming.getObservedAt().toInstant());
                if (existing == null) {
                    newObservations.add(incoming);
                    existingByTime.put(incoming.getObservedAt().toInstant(), incoming);
                } else {
                    existing.updateFrom(incoming);
                }
            }
            observationRepository.saveAll(newObservations);
        }
    }

    @Transactional
    public void upsertForecasts(List<WeatherForecast> forecasts) {
        if (forecasts.isEmpty()) return;

        Map<UUID, List<WeatherForecast>> grouped = forecasts.stream()
                .collect(Collectors.groupingBy(forecast -> forecast.getGrid().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        for (var group : grouped.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            gridRepository.findByIdForUpdate(group.getKey()).orElseThrow();
            List<OffsetDateTime> forecastTimes = group.getValue().stream()
                    .map(WeatherForecast::getForecastAt)
                    .toList();
            Map<Instant, WeatherForecast> existingByTime = forecastRepository
                    .findByGrid_IdAndForecastAtIn(group.getKey(), forecastTimes).stream()
                    .collect(Collectors.toMap(
                            forecast -> forecast.getForecastAt().toInstant(), forecast -> forecast));
            List<WeatherForecast> newForecasts = new ArrayList<>();

            for (WeatherForecast incoming : group.getValue()) {
                WeatherForecast existing = existingByTime.get(incoming.getForecastAt().toInstant());
                if (existing == null) {
                    newForecasts.add(incoming);
                    existingByTime.put(incoming.getForecastAt().toInstant(), incoming);
                } else {
                    existing.updateIfNewer(incoming);
                }
            }
            forecastRepository.saveAll(newForecasts);
        }
    }

    @Transactional(readOnly = true)
    public List<WeatherForecast> findForecasts(UUID gridId, OffsetDateTime from, OffsetDateTime to) {
        return forecastRepository.findRange(gridId, from, to);
    }

    @Transactional(readOnly = true)
    public Optional<WeatherObservation> findObservation(UUID gridId, OffsetDateTime observedAt) {
        return observationRepository.findByGrid_IdAndObservedAt(gridId, observedAt);
    }

}
