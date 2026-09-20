package com.codeit.otboo.batch.notification.processor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.batch.notification.reader.WeatherNotificationGrid;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeatherNotificationProcessorTest {
    private final KmaClient client = mock(KmaClient.class);
    private final LocalDate date = LocalDate.of(2026, 12, 31);
    private final OffsetDateTime base = OffsetDateTime.parse("2026-12-31T17:00:00+09:00");
    private final Clock clock = Clock.fixed(Instant.parse("2026-12-31T09:00:00Z"), ZoneOffset.UTC);
    private final WeatherNotificationGrid grid = new WeatherNotificationGrid(UUID.randomUUID(), 60, 127,
            Arrays.asList("서울시", "은평구", "진관동", null));
    private final WeatherNotificationProcessor processor = new WeatherNotificationProcessor(client, date, clock);

    private List<KmaForecastPointDto> points(int cloudyHours, int rainHour, int rainType) {
        var points = new ArrayList<KmaForecastPointDto>();
        for (int hour = 0; hour < 24; hour++) {
            var at = date.plusDays(1).atTime(hour, 0).atOffset(ZoneOffset.ofHours(9));
            // UTC 입력이어도 KST 날짜와 시간을 사용한다.
            at = at.withOffsetSameInstant(ZoneOffset.UTC);
            points.add(new KmaForecastPointDto(at, "TMP", String.valueOf(14 + hour)));
            points.add(new KmaForecastPointDto(at, "PTY", hour == rainHour ? String.valueOf(rainType) : "0"));
            points.add(new KmaForecastPointDto(at, "SKY", hour < cloudyHours ? "3" : "1"));
        }
        points.add(new KmaForecastPointDto(date.plusDays(1).atTime(6, 0).atOffset(ZoneOffset.ofHours(9)), "TMN", "14.0"));
        points.add(new KmaForecastPointDto(date.plusDays(1).atTime(15, 0).atOffset(ZoneOffset.ofHours(9)), "TMX", "27.5"));
        return points;
    }

    private void given(List<KmaForecastPointDto> points) {
        when(client.findDailyNotificationForecast(base, 60, 127))
                .thenReturn(Optional.of(new KmaForecastBundleDto(base, 60, 127, points)));
    }

    @ParameterizedTest
    @CsvSource({"11,-1,0,맑음", "12,-1,0,흐림", "24,14,1,14시부터 비 예정",
            "0,0,3,0시부터 눈 예정", "0,23,2,23시부터 비/눈 예정", "0,1,4,1시부터 비 예정"})
    void formatsTomorrowForecastAndPreservesDecimals(int cloudy, int hour, int type, String text) {
        given(points(cloudy, hour, type));
        var message = processor.process(grid);
        assertThat(message.payload().title()).isEqualTo("내일 날씨 예보입니다. | 서울시 은평구 진관동");
        assertThat(message.payload().content()).isEqualTo("최고 27.5도, 최저 14도 | " + text);
        assertThat(message.deduplicationKey()).isEqualTo("WEATHER_FORECAST:2027-01-01");
        assertThat(message.eventId().version()).isEqualTo(7);
        verify(client).findDailyNotificationForecast(base, 60, 127);
    }

    @Test
    void usesTmpOnlyWhenMissingDailyTemperatureAndAll24HoursExist() {
        var points = points(0, -1, 0);
        points.removeIf(point -> point.category().equals("TMX"));
        given(points);
        assertThat(processor.process(grid).payload().content()).isEqualTo("최고 37도, 최저 14도 | 맑음");
        points.removeIf(point -> point.category().equals("TMP") && point.forecastAt().getHour() == 0);
        assertThat(processor.process(grid)).isNull();
    }

    @Test
    void completeDailyTemperaturesDoNotRequireTmp() {
        var points = points(0, -1, 0);
        points.removeIf(point -> point.category().equals("TMP"));
        given(points);
        assertThat(processor.process(grid)).isNotNull();
    }

    @Test
    void rejectsDuplicateHoursEvenIfCountLooksComplete() {
        var points = points(0, -1, 0);
        points.add(points.get(1));
        given(points);
        assertThat(processor.process(grid)).isNull();
    }

    @Test
    void doesNotTreatMissingPrecipitationAsClear() {
        var points = points(0, -1, 0);
        points.removeIf(point -> point.category().equals("PTY"));
        given(points);
        assertThat(processor.process(grid)).isNull();
    }

    @Test
    void failedCollectionAndMissingRegionAreFiltered() {
        when(client.findDailyNotificationForecast(base, 60, 127)).thenReturn(Optional.empty());
        assertThat(processor.process(grid)).isNull();
        clearInvocations(client);
        assertThat(processor.process(new WeatherNotificationGrid(grid.id(), 60, 127, List.of("")))).isNull();
        verifyNoInteractions(client);
    }

    @Test
    void midnightStopsCollection() {
        var expired = new WeatherNotificationProcessor(client, date,
                Clock.fixed(Instant.parse("2026-12-31T15:00:00Z"), ZoneOffset.UTC));
        assertThat(expired.process(grid)).isNull();
        verifyNoInteractions(client);
    }
}
