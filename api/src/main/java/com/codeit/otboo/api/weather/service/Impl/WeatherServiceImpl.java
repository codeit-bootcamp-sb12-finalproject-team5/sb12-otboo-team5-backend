package com.codeit.otboo.api.weather.service.Impl;

import com.codeit.otboo.api.weather.dto.request.LocationReadRequest;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.dto.response.HumidityDto;
import com.codeit.otboo.api.weather.dto.response.PrecipitationDto;
import com.codeit.otboo.api.weather.dto.response.TemperatureDto;
import com.codeit.otboo.api.weather.dto.response.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.response.WeatherDto;
import com.codeit.otboo.api.weather.dto.response.WindSpeedDto;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.api.weather.service.WeatherService;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WeatherServiceImpl implements WeatherService {
    private final LocationService locationService;
    private final WeatherRepository weatherRepository;
    private final KmaClient kmaClient;

    @Override
    public List<WeatherDto> findWeather(WeatherReadRequest request) {
        WeatherGrid grid = findGrid(request.longitude(), request.latitude());
        OffsetDateTime targetAt = nextThreeHourSlot(OffsetDateTime.now(KmaTimeCalculator.KST));
        ensureRequiredData(grid, targetAt);

        return assemble(grid, targetAt, request.longitude(), request.latitude());
    }

    @Override
    public WeatherAPILocation findLocation(LocationReadRequest request) {
        WeatherGrid grid = findGrid(request.longitude(), request.latitude());
        return toLocationResponse(grid, request.longitude(), request.latitude());
    }

    private WeatherGrid findGrid(double longitude, double latitude) {
        var coordinate = locationService.convert(longitude, latitude);
        return locationService.findOrCreate(coordinate.x(), coordinate.y(), longitude, latitude);
    }

    private OffsetDateTime nextThreeHourSlot(OffsetDateTime now) {
        OffsetDateTime hour = KmaTimeCalculator.normalizeToKstHour(now);
        if (now.isEqual(hour) && hour.getHour() % 3 == 0) return hour;
        return hour.plusHours(3 - hour.getHour() % 3);
    }

    private WeatherAPILocation toLocationResponse(WeatherGrid grid, double longitude, double latitude) {
        return new WeatherAPILocation(latitude, longitude, grid.getNx(), grid.getNy(), grid.getLocationNames());
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

            List<WeatherForecast> incoming = normalizeForecasts(grid, bundle);

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
                    List.of(normalizeObservation(grid, observation))));
        }
    }

    private List<WeatherForecast> normalizeForecasts(WeatherGrid grid, KmaForecastBundleDto bundle) {

        Map<LocalDate, BigDecimal> dailyMinByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> dailyMaxByDate = new HashMap<>();
        Map<OffsetDateTime, Map<String, String>> grouped = new LinkedHashMap<>();

        for (KmaForecastPointDto point : bundle.points()) {

            if ("TMN".equals(point.category())) {
                dailyMinByDate.put(point.forecastAt().toLocalDate(), number(point.value()));
                continue;
            }

            if ("TMX".equals(point.category())) {
                dailyMaxByDate.put(point.forecastAt().toLocalDate(), number(point.value()));
                continue;
            }

            if (point.forecastAt().getHour() % 3 != 0 || point.forecastAt().getMinute() != 0) continue;

            grouped
                .computeIfAbsent(point.forecastAt(), ignored -> new LinkedHashMap<>())
                .put(point.category(), point.value());
        }

        List<WeatherForecast> result = new ArrayList<>();

        for (var entry : grouped.entrySet()) {

            Map<String, String> values = entry.getValue();
            int precipitationCode = integer(values.get("PTY"), 0);
            LocalDate slotDate = entry.getKey().toLocalDate();

            result.add(WeatherForecast.builder()
                    .grid(grid)
                    .forecastedAt(bundle.forecastedAt())
                    .forecastAt(entry.getKey())
                    .temperature(number(values.get("TMP")))
                    .humidity(number(values.get("REH")))
                    .precipitationType(precipitationType(precipitationCode))
                    .precipitationAmount(precipitationAmount(values.get("PCP")))
                    .precipitationProbability(number(values.get("POP")))
                    .skyStatus(skyStatus(integer(values.get("SKY"), 1)))
                    .windSpeed(number(values.get("WSD")))
                    .windDirection(number(values.get("VEC")))
                    .minTemperature(dailyMinByDate.get(slotDate))
                    .maxTemperature(dailyMaxByDate.get(slotDate))
                    .build());
        }

        return result;
    }

    private WeatherObservation normalizeObservation(WeatherGrid grid, KmaObservationDto observation) {

        int precipitationCode = observation.value("PTY", BigDecimal.ZERO).intValue();

        return WeatherObservation.builder()
                .grid(grid)
                .observedAt(observation.observedAt())
                .temperature(observation.value("T1H", null))
                .humidity(observation.value("REH", null))
                .precipitationType(precipitationType(precipitationCode))
                .precipitationAmount(observation.value("RN1", BigDecimal.ZERO))
                .windSpeed(observation.value("WSD", BigDecimal.ZERO))
                .windDirection(observation.value("VEC", BigDecimal.ZERO))
                .build();
    }

    private BigDecimal number(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private BigDecimal precipitationAmount(String value) {
        if (value == null || value.contains("없음")) return BigDecimal.ZERO;
        if (value.contains("미만")) return new BigDecimal("0.5");
        String numeric = value.replaceAll("[^0-9.~]", "");
        if (numeric.contains("~")) numeric = numeric.substring(0, numeric.indexOf('~'));
        try { return new BigDecimal(numeric); }
        catch (Exception ignored) { return BigDecimal.ZERO; }
    }

    private String precipitationType(int code) {
        return switch (code) {
            case 1, 4, 5 -> "RAIN";
            case 2, 6 -> "SLEET";
            case 3, 7 -> "SNOW";
            default -> "NONE";
        };
    }

    private String skyStatus(int code) {
        return switch (code) {
            case 1 -> "CLEAR";
            case 3 -> "MOSTLY_CLOUDY";
            default -> "CLOUDY";
        };
    }


    private List<WeatherDto> assemble(
        WeatherGrid grid,
        OffsetDateTime targetAt,
        double longitude,
        double latitude
    ) {
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

        List<WeatherDto> result = new ArrayList<>();

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
                new WeatherDto(
                    current.getId(),
                    current.getForecastedAt().withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDateTime(),
                    current.getForecastAt().withOffsetSameInstant(KmaTimeCalculator.KST).toLocalDateTime(),
                    toLocationResponse(grid, longitude, latitude),
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
