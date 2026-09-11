package com.codeit.otboo.support.weather.normalize;

import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KmaForecastNormalizer {

    private KmaForecastNormalizer() {}

    public static List<WeatherForecast> normalizeForecasts(WeatherGrid grid, KmaForecastBundleDto bundle) {

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

    public static WeatherObservation normalizeObservation(WeatherGrid grid, KmaObservationDto observation) {

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

    public static BigDecimal number(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static BigDecimal precipitationAmount(String value) {
        if (value == null || value.contains("없음")) return BigDecimal.ZERO;
        if (value.contains("미만")) return new BigDecimal("0.5");
        String numeric = value.replaceAll("[^0-9.~]", "");
        if (numeric.contains("~")) numeric = numeric.substring(0, numeric.indexOf('~'));
        try { return new BigDecimal(numeric); }
        catch (Exception ignored) { return BigDecimal.ZERO; }
    }

    public static String precipitationType(int code) {
        return switch (code) {
            case 1, 4, 5 -> "RAIN";
            case 2, 6 -> "SLEET";
            case 3, 7 -> "SNOW";
            default -> "NONE";
        };
    }

    public static String skyStatus(int code) {
        return switch (code) {
            case 1 -> "CLEAR";
            case 3 -> "MOSTLY_CLOUDY";
            default -> "CLOUDY";
        };
    }
}
