package com.codeit.otboo.api.weather.service;

import com.codeit.otboo.api.weather.dto.response.HumidityDto;
import com.codeit.otboo.api.weather.dto.response.PrecipitationDto;
import com.codeit.otboo.api.weather.dto.response.TemperatureDto;
import com.codeit.otboo.api.weather.dto.response.WeatherViewData;
import com.codeit.otboo.api.weather.dto.response.WindSpeedDto;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.common.config.CacheConfig;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.normalize.KmaForecastNormalizer;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WeatherViewCacheService {
    private final WeatherRepository weatherRepository;
    private final KmaClient kmaClient;

    @Cacheable(
            cacheNames = CacheConfig.WEATHER_CACHE,
            key = "#grid.nx + ':' + #grid.ny + ':' + #targetAt",
            sync = true)
    public List<WeatherViewData> findWeatherView(WeatherGrid grid, OffsetDateTime targetAt) {
        ensureRequiredData(grid, targetAt);
        return assemble(grid, targetAt);
    }

    private void ensureRequiredData(WeatherGrid grid, OffsetDateTime targetAt) {

        List<WeatherForecast> forecasts = weatherRepository.findForecasts(
            grid.getId(),
            targetAt.truncatedTo(ChronoUnit.DAYS),
            targetAt.truncatedTo(ChronoUnit.DAYS).plusDays(6)
        );

        if (forecasts.stream().noneMatch(forecast -> forecast.getForecastAt().isEqual(targetAt))) {
            KmaForecastBundleDto bundle = kmaClient.findLatestVillageForecast(grid.getNx(), grid.getNy())
                .orElseThrow(() -> new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE));

            List<WeatherForecast> incoming = KmaForecastNormalizer.normalizeForecasts(grid, bundle);

            if (incoming.stream().noneMatch(forecast -> forecast.getForecastAt().isEqual(targetAt))) {
                throw new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE);
            }
            weatherRepository.upsertForecasts(incoming);
        }

        OffsetDateTime previousAt = targetAt.minusDays(1);

        if (weatherRepository.findObservation(grid.getId(), previousAt).isEmpty()) {
            kmaClient.findObservation(previousAt, grid.getNx(), grid.getNy())
                .filter(observation -> observation.observedAt().isEqual(previousAt))
                .ifPresent(observation -> weatherRepository.upsertObservations(
                    List.of(KmaForecastNormalizer.normalizeObservation(grid, observation))));
        }
    }

    private List<WeatherViewData> assemble(WeatherGrid grid, OffsetDateTime targetAt) {
        LocalDate firstDate = targetAt.toLocalDate();

        List<WeatherForecast> forecasts = weatherRepository.findForecasts(
                grid.getId(),
                firstDate.atStartOfDay().atOffset(KmaTimeCalculator.KST),
                firstDate.plusDays(6).atStartOfDay().atOffset(KmaTimeCalculator.KST)
        );

        Map<LocalDate, List<WeatherForecast>> byDate = forecasts.stream()
                .collect(Collectors.groupingBy(
                        forecast -> forecast
                            .getForecastAt()
                            .withOffsetSameInstant(KmaTimeCalculator.KST)
                            .toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList())
                );

        List<SelectedDay> selected = selectRepresentativeDays(byDate, targetAt);

        WeatherObservation previousObservation = weatherRepository.findObservation(
                grid.getId(),
                targetAt.minusDays(1))
            .orElse(null);

        WeatherForecast previousSelectedForecast = null;
        LocalDate previousDate = null;

        List<WeatherViewData> result = new ArrayList<>();

        for (SelectedDay day : selected) {
            WeatherForecast current = day.representative();

            boolean firstDay = current.getForecastAt().withOffsetSameInstant(KmaTimeCalculator.KST)
                .toLocalDate().equals(firstDate);

            boolean adjacentDay = previousDate != null && previousDate.plusDays(1).equals(
                current.getForecastAt().withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDate());

            BigDecimal previousTemperature = firstDay
                    ? (previousObservation == null ? null : previousObservation.getTemperature())
                    : (adjacentDay ? previousSelectedForecast.getTemperature() : null);

            BigDecimal previousHumidity = firstDay
                    ? (previousObservation == null ? null : previousObservation.getHumidity())
                    : (adjacentDay ? previousSelectedForecast.getHumidity() : null);

            BigDecimal temperatureDiff = difference(current.getTemperature(), previousTemperature);
            BigDecimal humidityDiff = difference(current.getHumidity(), previousHumidity);
            BigDecimal min = current.getMinTemperature() != null ? current.getMinTemperature()
                    : day.all().stream().map(WeatherForecast::getTemperature)
                            .filter(Objects::nonNull).min(BigDecimal::compareTo)
                            .orElse(value(current.getTemperature()));

            BigDecimal max = current.getMaxTemperature() != null ? current.getMaxTemperature()
                    : day.all().stream().map(WeatherForecast::getTemperature)
                            .filter(Objects::nonNull).max(BigDecimal::compareTo)
                            .orElse(value(current.getTemperature()));

            BigDecimal rainAmount = day.all().stream()
                    .map(WeatherForecast::getPrecipitationAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal probability = day.all().stream()
                    .map(WeatherForecast::getPrecipitationProbability)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            String precipitationType = day.all().stream()
                    .map(WeatherForecast::getPrecipitationType)
                    .filter(type -> type != null && !type.isBlank() && !"NONE".equals(type))
                    .findFirst().orElse("NONE");

            BigDecimal wind = value(current.getWindSpeed());

            result.add(
                new WeatherViewData(
                    current.getId(),
                    current.getForecastedAt().withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDateTime(),
                    current.getForecastAt().withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDateTime(),
                    blankDefault(current.getSkyStatus(), "CLOUDY"),
                    new PrecipitationDto(precipitationType, rainAmount, probability),
                    new HumidityDto(value(current.getHumidity()), humidityDiff),
                    new TemperatureDto(value(current.getTemperature()), temperatureDiff, min, max),
                    new WindSpeedDto(wind,
                            wind.compareTo(BigDecimal.valueOf(9)) >= 0 ? "STRONG"
                                    : wind.compareTo(BigDecimal.valueOf(4)) >= 0
                                            ? "MODERATE" : "WEAK"
                    )
                )
            );

            previousSelectedForecast = current;
            previousDate = current.getForecastAt().withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDate();
        }

        return result;
    }

    private List<SelectedDay> selectRepresentativeDays(
            Map<LocalDate, List<WeatherForecast>> byDate, OffsetDateTime targetAt) {

        List<SelectedDay> selected = new ArrayList<>();

        for (int offset = 0; offset < 6; offset++) {
            LocalDate date = targetAt.toLocalDate().plusDays(offset);
            List<WeatherForecast> daily = byDate.getOrDefault(date, List.of());

            if (daily.isEmpty()) continue;

            OffsetDateTime desired = date.atTime(targetAt.toLocalTime()).atOffset(KmaTimeCalculator.KST);
            WeatherForecast representative = daily.stream()
                    .filter(forecast -> !forecast.getForecastAt().isAfter(desired))
                    .max(Comparator.comparing(WeatherForecast::getForecastAt))
                    .orElseGet(() -> daily.stream()
                            .min(Comparator.comparing(WeatherForecast::getForecastAt)).orElseThrow());

            selected.add(new SelectedDay(representative, daily));
        }

        return selected;
    }

    private BigDecimal difference(BigDecimal current, BigDecimal previous) {
        return current == null || previous == null ? null : current.subtract(previous);
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record SelectedDay(
        WeatherForecast representative, List<WeatherForecast> all) {
    }
}
