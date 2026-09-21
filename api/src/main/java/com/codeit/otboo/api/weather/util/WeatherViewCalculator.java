package com.codeit.otboo.api.weather.util;

import com.codeit.otboo.domain.weather.dto.WeatherInfoResponse;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 화면 응답과 저장용 날씨 정보에 사용하는 동일한 계산 규칙. */
public final class WeatherViewCalculator {
    private WeatherViewCalculator() {}

    public static WeatherInfoResponse calculate(WeatherForecast current,
            List<WeatherForecast> daily, BigDecimal previousTemperature) {
        BigDecimal min = current.getMinTemperature() != null ? current.getMinTemperature()
                : daily.stream().map(WeatherForecast::getTemperature)
                        .filter(Objects::nonNull).min(BigDecimal::compareTo)
                        .orElse(value(current.getTemperature()));

        BigDecimal max = current.getMaxTemperature() != null ? current.getMaxTemperature()
                : daily.stream().map(WeatherForecast::getTemperature)
                        .filter(Objects::nonNull).max(BigDecimal::compareTo)
                        .orElse(value(current.getTemperature()));

        BigDecimal rainAmount = daily.stream()
                .map(WeatherForecast::getPrecipitationAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal probability = daily.stream()
                .map(WeatherForecast::getPrecipitationProbability)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        PrecipitationType precipitationType = daily.stream()
                .map(WeatherForecast::getPrecipitationType)
                .filter(type -> type != null && type != PrecipitationType.NONE)
                .findFirst().orElse(PrecipitationType.NONE);

        return new WeatherInfoResponse(
            current.getSkyStatus() == null ? SkyStatus.CLOUDY : current.getSkyStatus(),
            precipitationType, rainAmount, probability, value(current.getTemperature()),
            current.getTemperature() == null || previousTemperature == null ? null
                : current.getTemperature().subtract(previousTemperature), min, max);
    }

    public static Optional<WeatherForecast> representative(List<WeatherForecast> daily,
            OffsetDateTime desired) {
        return daily.stream().filter(forecast -> !forecast.getForecastAt().isAfter(desired))
            .max(Comparator.comparing(WeatherForecast::getForecastAt))
            .or(() -> daily.stream().min(Comparator.comparing(WeatherForecast::getForecastAt)));
    }

    public static OffsetDateTime forecastSlotForToday(OffsetDateTime now) {
        OffsetDateTime hour = KmaTimeCalculator.normalizeToKstHour(now);
        if (hour.getHour() >= 21) return hour.withHour(21);
        if (now.isEqual(hour) && hour.getHour() % 3 == 0) return hour;
        return hour.plusHours(3 - hour.getHour() % 3);
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
