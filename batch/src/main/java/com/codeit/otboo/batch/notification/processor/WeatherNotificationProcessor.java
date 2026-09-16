package com.codeit.otboo.batch.notification.processor;

import com.codeit.otboo.batch.notification.reader.WeatherNotificationGrid;
import com.codeit.otboo.domain.notification.entity.NotificationType;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.WeatherNotificationCreateEvent;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.fasterxml.uuid.Generators;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
public class WeatherNotificationProcessor implements
        ItemProcessor<WeatherNotificationGrid, NotificationCreateMessage<WeatherNotificationCreateEvent>> {
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private final KmaClient client;
    private final LocalDate collectionDate;
    private final Clock clock;

    public WeatherNotificationProcessor(KmaClient client, LocalDate collectionDate, Clock clock) {
        this.client = client;
        this.collectionDate = collectionDate;
        this.clock = clock;
    }

    @Override
    public NotificationCreateMessage<WeatherNotificationCreateEvent> process(WeatherNotificationGrid grid) {
        LocalDate forecastDate = collectionDate.plusDays(1);
        if (!LocalDate.now(clock.withZone(KST)).isBefore(forecastDate)) {
            return skip(grid, "예보 대상일 도달");
        }
        String region = grid.locationNames().stream().filter(name -> name != null && !name.isBlank())
                .map(String::strip).collect(Collectors.joining(" "));
        if (region.isBlank()) return skip(grid, "지역명 누락");
        var base = collectionDate.atTime(17, 0).atOffset(KST);
        var bundle = client.findDailyNotificationForecast(base, grid.nx(), grid.ny());
        if (bundle.isEmpty()) return skip(grid, "17시 발표 예보 수집 실패");
        if (!bundle.get().forecastedAt().isEqual(base)
                || bundle.get().nx() != grid.nx() || bundle.get().ny() != grid.ny()) {
            return skip(grid, "발표본 또는 격자 불일치");
        }
        try {
            String content = summarize(bundle.get(), forecastDate, grid);
            String title = "내일 날씨 예보입니다. | " + region;
            if (title.length() > 100) return skip(grid, "제목 길이 초과");
            return new NotificationCreateMessage<>(
                    Generators.timeBasedEpochGenerator().generate(),
                    NotificationCreateMessage.CURRENT_SCHEMA_VERSION,
                    NotificationType.WEATHER_FORECAST,
                    OffsetDateTime.now(clock),
                    "WEATHER_FORECAST:" + forecastDate,
                    new WeatherNotificationCreateEvent(grid.id(), title, content));
        } catch (IllegalArgumentException exception) {
            return skip(grid, exception.getMessage());
        }
    }

    private String summarize(KmaForecastBundleDto bundle, LocalDate date, WeatherNotificationGrid grid) {
        Map<String, Map<Integer, BigDecimal>> values = new HashMap<>();
        for (var point : bundle.points()) {
            var time = point.forecastAt().withOffsetSameInstant(KST);
            if (!time.toLocalDate().equals(date)) continue;
            if (!java.util.Set.of("TMN", "TMX", "TMP", "PTY", "SKY").contains(point.category())) continue;
            if (time.getMinute() != 0 || time.getSecond() != 0 || time.getNano() != 0) {
                throw new IllegalArgumentException("정시가 아닌 예보");
            }
            BigDecimal value = new BigDecimal(point.value());
            if (value.compareTo(new BigDecimal("-900")) <= 0) {
                throw new IllegalArgumentException("결측 기상 값");
            }
            var hours = values.computeIfAbsent(point.category(), key -> new HashMap<>());
            if (hours.putIfAbsent(time.getHour(), value) != null) {
                throw new IllegalArgumentException("중복 시각/category");
            }
        }
        BigDecimal min = dailyTemperature(values.get("TMN"));
        BigDecimal max = dailyTemperature(values.get("TMX"));
        if (min == null || max == null) {
            var tmp = complete(values, "TMP");
            if (min == null) min = tmp.values().stream().min(BigDecimal::compareTo).orElseThrow();
            if (max == null) max = tmp.values().stream().max(BigDecimal::compareTo).orElseThrow();
            log.info("[WEATHER-NOTIFICATION] grid={}, forecastDate={}, temperatureSource=TMP fallback",
                    grid.id(), date);
        }
        if (min.compareTo(max) > 0) throw new IllegalArgumentException("최저기온이 최고기온보다 큼");
        var pty = complete(values, "PTY");
        String summary = null;
        for (int hour = 0; hour < 24; hour++) {
            int type = code(pty.get(hour));
            String precipitation = switch (type) {
                case 0 -> null;
                case 1, 4 -> "비";
                case 2 -> "비/눈";
                case 3 -> "눈";
                default -> throw new IllegalArgumentException("지원하지 않는 PTY");
            };
            if (summary == null && precipitation != null) {
                summary = hour + "시부터 " + precipitation + " 예정";
            }
        }
        if (summary == null) {
            var sky = complete(values, "SKY");
            int cloudy = 0;
            for (var value : sky.values()) {
                int type = code(value);
                if (type == 3 || type == 4) cloudy++;
                else if (type != 1) throw new IllegalArgumentException("지원하지 않는 SKY");
            }
            summary = cloudy >= 12 ? "흐림" : "맑음";
        }
        return "최고 %s도, 최저 %s도 | %s".formatted(
                max.stripTrailingZeros().toPlainString(), min.stripTrailingZeros().toPlainString(), summary);
    }

    private BigDecimal dailyTemperature(Map<Integer, BigDecimal> values) {
        if (values == null || values.isEmpty()) return null;
        BigDecimal first = values.values().iterator().next();
        if (values.values().stream().anyMatch(value -> value.compareTo(first) != 0)) {
            throw new IllegalArgumentException("일 최저/최고 값 불일치");
        }
        return first;
    }

    private Map<Integer, BigDecimal> complete(Map<String, Map<Integer, BigDecimal>> values, String category) {
        var hours = values.get(category);
        if (hours == null || hours.size() != 24) throw new IllegalArgumentException(category + " 24시간 미완성");
        return hours;
    }

    private int code(BigDecimal value) {
        try {
            return value.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("기상 코드가 정수가 아님", exception);
        }
    }

    private NotificationCreateMessage<WeatherNotificationCreateEvent> skip(WeatherNotificationGrid grid, String reason) {
        log.warn("[WEATHER-NOTIFICATION] 미발송 grid={}, collectionDate={}, reason={}", grid.id(), collectionDate, reason);
        return null;
    }
}
