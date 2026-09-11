package com.codeit.otboo.api.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.api.weather.dto.request.LocationReadRequest;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.dto.response.WeatherAPILocation;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.api.weather.service.Impl.WeatherServiceImpl;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.support.weather.client.KakaoClient;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

class WeatherServiceImplTest {
    private final WeatherRepository repository = mock(WeatherRepository.class);
    private final KakaoClient kakao = mock(KakaoClient.class);
    private final KmaClient kma = mock(KmaClient.class);
    private final WeatherServiceImpl service = new WeatherServiceImpl(
        new LocationService(repository, kakao), new WeatherViewCacheService(repository, kma));
    private final WeatherGrid grid = WeatherGrid.builder().id(UUID.randomUUID()).nx(60).ny(127)
        .region1Depth("서울특별시").region2Depth("중구").region3Depth("명동").build();
    private final OffsetDateTime target = OffsetDateTime.parse("2026-09-08T15:00:00+09:00");
    private final WeatherReadRequest request = new WeatherReadRequest(126.978, 37.5665);
    private MockedStatic<OffsetDateTime> time;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = target.minusMinutes(30);
        time = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS);
        time.when(() -> OffsetDateTime.now(KmaTimeCalculator.KST)).thenReturn(now);
        when(repository.findGrid(60, 127)).thenReturn(Optional.of(grid));
    }

    @AfterEach
    void tearDown() {
        time.close();
    }

    @Test
    void returnsOriginalCoordinatesAndStoredGridWithoutSwappingAxes() {
        WeatherAPILocation result = service.findLocation(new LocationReadRequest(126.978, 37.5665));
        assertThat(result).isEqualTo(new WeatherAPILocation(37.5665, 126.978, 60, 127,
            List.of("서울특별시", "중구", "명동", "")));
        verifyNoInteractions(kakao, kma);
    }

    @Test
    void databaseHitReturnsDailyForecastsAndPreviousDayDifferencesWithoutExternalCalls() {
        WeatherForecast today = forecast(target, "25", "60");
        WeatherForecast tomorrow = forecast(target.plusDays(1), "28", "70");
        when(repository.findForecasts(eq(grid.getId()), any(), any())).thenReturn(List.of(today, tomorrow));
        when(repository.findObservation(grid.getId(), target.minusDays(1)))
            .thenReturn(Optional.of(observation("22", "65")));

        var result = service.findWeather(request);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(today.getId());
        assertThat(result.get(0).forecastAt()).isEqualTo(target.toLocalDateTime());
        assertThat(result.get(0).temperature().comparedToDayBefore()).isEqualByComparingTo("3");
        assertThat(result.get(0).humidity().comparedToDayBefore()).isEqualByComparingTo("-5");
        assertThat(result.get(1).temperature().comparedToDayBefore()).isEqualByComparingTo("3");
        assertThat(result.get(1).humidity().comparedToDayBefore()).isEqualByComparingTo("10");
        verify(repository, times(2)).findForecasts(eq(grid.getId()), any(), any());
        verify(repository, never()).upsertForecasts(any());
        verifyNoInteractions(kakao, kma);
    }

    @Test
    void lateEveningKeepsTodayAsFirstOfFiveForecastDates() {
        var now = OffsetDateTime.parse("2026-09-10T23:30:00+09:00");
        time.when(() -> OffsetDateTime.now(KmaTimeCalculator.KST)).thenReturn(now);
        var today = now.withHour(21).withMinute(0);
        var forecasts = java.util.stream.IntStream.range(0, 5)
                .mapToObj(day -> forecast(day == 4 ? today.plusDays(day).withHour(0)
                        : today.plusDays(day), "25", "60")).toList();
        when(repository.findForecasts(eq(grid.getId()), any(), any())).thenReturn(forecasts);
        when(repository.findObservation(grid.getId(), today.minusDays(1)))
                .thenReturn(Optional.of(observation("22", "65")));
        var result = service.findWeather(request);
        assertThat(result).hasSize(5);
        assertThat(result.get(0).forecastAt()).isEqualTo(today.toLocalDateTime());
        assertThat(result.get(4).forecastAt()).isEqualTo(today.plusDays(4).withHour(0).toLocalDateTime());
        verifyNoInteractions(kma);
    }

    @Test
    void missingDataIsNormalizedSavedAndRereadWithDatabaseId() {
        WeatherForecast stored = forecast(target, "25", "60");
        when(repository.findForecasts(eq(grid.getId()), any(), any()))
            .thenReturn(List.of(), List.of(stored));
        when(repository.findObservation(grid.getId(), target.minusDays(1)))
            .thenReturn(Optional.empty(), Optional.of(observation("22", "65")));
        when(kma.findLatestVillageForecast(60, 127)).thenReturn(Optional.of(new KmaForecastBundleDto(
            target.minusHours(1), 60, 127, List.of(
                point(target, "TMP", "25"), point(target, "REH", "60"),
                point(target, "PCP", "1.0mm 미만"), point(target, "PTY", "2"),
                point(target, "SKY", "3"), point(target, "POP", "40"),
                point(target.withHour(6), "TMN", "18"), point(target.withHour(15), "TMX", "29"),
                point(target.plusHours(1), "TMP", "26")))));
        when(kma.findObservation(target.minusDays(1), 60, 127)).thenReturn(Optional.of(
            new KmaObservationDto(target.minusDays(1), 60, 127, Map.of("T1H", "22", "REH", "65"))));

        var result = service.findWeather(request);

        verify(repository).upsertForecasts(argThat(values -> values.size() == 1
            && values.get(0).getForecastAt().equals(target)
            && values.get(0).getMinTemperature().compareTo(new BigDecimal("18")) == 0
            && values.get(0).getMaxTemperature().compareTo(new BigDecimal("29")) == 0
            && values.get(0).getPrecipitationAmount().compareTo(new BigDecimal("0.5")) == 0
            && values.get(0).getPrecipitationType().equals("SLEET")
            && values.get(0).getSkyStatus().equals("MOSTLY_CLOUDY")));
        verify(repository).upsertObservations(argThat(values -> values.size() == 1
            && values.get(0).getObservedAt().equals(target.minusDays(1))));
        var order = inOrder(repository);
        order.verify(repository).findForecasts(eq(grid.getId()), any(), any());
        order.verify(repository).upsertForecasts(any());
        order.verify(repository).upsertObservations(any());
        order.verify(repository).findForecasts(eq(grid.getId()), any(), any());
        assertThat(result.get(0).id()).isEqualTo(stored.getId());
        assertThat(result.get(0).temperature().comparedToDayBefore()).isEqualByComparingTo("3");
    }

    @Test
    void unavailablePreviousObservationLeavesComparisonNull() {
        when(repository.findForecasts(eq(grid.getId()), any(), any()))
            .thenReturn(List.of(forecast(target, "25", "60")));
        var result = service.findWeather(request);
        assertThat(result.get(0).temperature().comparedToDayBefore()).isNull();
        assertThat(result.get(0).humidity().comparedToDayBefore()).isNull();
        verify(kma).findObservation(target.minusDays(1), 60, 127);
        verify(kma, never()).findLatestVillageForecast(anyInt(), anyInt());
    }

    @Test
    void unavailableForecastRaisesDomainErrorWithoutSaving() {
        assertThatThrownBy(() -> service.findWeather(request))
            .isInstanceOfSatisfying(WeatherException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.WEATHER_DATA_UNAVAILABLE));
        verify(repository, never()).upsertForecasts(any());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-08T15:00:00+09:00, 2026-09-08T15:00:00+09:00",
        "2026-09-08T15:00:00.000000001+09:00, 2026-09-08T18:00:00+09:00",
        "2026-09-08T14:30:00+09:00, 2026-09-08T15:00:00+09:00",
        "2026-09-10T20:59:59+09:00, 2026-09-10T21:00:00+09:00",
        "2026-09-10T21:00:00+09:00, 2026-09-10T21:00:00+09:00",
        "2026-09-10T21:00:00.000000001+09:00, 2026-09-10T21:00:00+09:00",
        "2026-09-10T23:59:59+09:00, 2026-09-10T21:00:00+09:00",
        "2026-12-31T23:30:00+09:00, 2026-12-31T21:00:00+09:00",
        "2027-01-01T00:00:00+09:00, 2027-01-01T00:00:00+09:00",
        "2026-09-10T14:30:00Z, 2026-09-10T21:00:00+09:00"
    })
    void roundsUpWithinTodayAndCapsAtNinePm(String now, String expected) {
        OffsetDateTime actual = ReflectionTestUtils.invokeMethod(service, "forecastSlotForToday", OffsetDateTime.parse(now));
        assertThat(actual).isEqualTo(OffsetDateTime.parse(expected));
    }

    private WeatherForecast forecast(OffsetDateTime at, String temperature, String humidity) {
        return WeatherForecast.builder().id(UUID.randomUUID()).grid(grid).forecastAt(at)
            .forecastedAt(target.minusHours(1)).temperature(new BigDecimal(temperature))
            .humidity(new BigDecimal(humidity)).build();
    }

    private WeatherObservation observation(String temperature, String humidity) {
        return WeatherObservation.builder().grid(grid).observedAt(target.minusDays(1))
            .temperature(new BigDecimal(temperature)).humidity(new BigDecimal(humidity)).build();
    }

    private KmaForecastPointDto point(OffsetDateTime at, String category, String value) {
        return new KmaForecastPointDto(at, category, value);
    }
}
