package com.codeit.otboo.api.weather.repository;

import com.codeit.otboo.domain.weather.dto.WeatherInfoResponse;
import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
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
    public WeatherGrid fillGridLocationNames(UUID gridId, List<String> names) {
        WeatherGrid grid = gridRepository.findByIdForUpdate(gridId).orElseThrow();
        if (grid.getLocationNames().get(0).isBlank()
                && !names.isEmpty() && !names.get(0).isBlank()) {
            grid.updateForRequest(names);
        }
        return grid;
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

    @Transactional(readOnly = true)
    public Optional<WeatherInfoResponse> findById(UUID weatherId) {
        return forecastRepository.findById(weatherId)
                .filter(forecast -> forecast.getTemperature() != null)
                .map(this::toWeatherInfo);
    }

    private WeatherInfoResponse toWeatherInfo(WeatherForecast forecast) {
        BigDecimal current = forecast.getTemperature();
        BigDecimal min = forecast.getMinTemperature();
        BigDecimal max = forecast.getMaxTemperature();

        if (min == null || max == null) {
            List<BigDecimal> temperatures = sameDayTemperatures(forecast);
            if (min == null) {
                min = temperatures.stream().min(BigDecimal::compareTo).orElse(current);
            }
            if (max == null) {
                max = temperatures.stream().max(BigDecimal::compareTo).orElse(current);
            }
        }

        return new WeatherInfoResponse(
                forecast.getSkyStatus() == null ? SkyStatus.CLOUDY : forecast.getSkyStatus(),
                forecast.getPrecipitationType() == null
                        ? PrecipitationType.NONE : forecast.getPrecipitationType(),
                zeroIfNull(forecast.getPrecipitationAmount()),
                zeroIfNull(forecast.getPrecipitationProbability()),
                current,
                comparedToDayBefore(forecast),
                min,
                max
        );
    }

    private List<BigDecimal> sameDayTemperatures(WeatherForecast forecast) {
        OffsetDateTime dayStart = forecast.getForecastAt()
                .withOffsetSameInstant(KmaTimeCalculator.KST)
                .truncatedTo(ChronoUnit.DAYS);

        return forecastRepository.findRange(forecast.getGrid().getId(), dayStart, dayStart.plusDays(1))
                .stream()
                .map(WeatherForecast::getTemperature)
                .filter(Objects::nonNull)
                .toList();
    }

    private BigDecimal comparedToDayBefore(WeatherForecast forecast) {
        UUID gridId = forecast.getGrid().getId();
        OffsetDateTime dayBefore = forecast.getForecastAt().minusDays(1);

        LocalDate forecastDate = forecast.getForecastAt()
                .withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDate();
        LocalDate today = OffsetDateTime.now(KmaTimeCalculator.KST).toLocalDate();

        BigDecimal previous = forecastDate.isEqual(today)
                ? observationRepository.findByGrid_IdAndObservedAt(gridId, dayBefore)
                        .map(WeatherObservation::getTemperature).orElse(null)
                : forecastRepository.findByGrid_IdAndForecastAtIn(gridId, List.of(dayBefore))
                        .stream().findFirst().map(WeatherForecast::getTemperature).orElse(null);

        return previous == null ? null : forecast.getTemperature().subtract(previous);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
