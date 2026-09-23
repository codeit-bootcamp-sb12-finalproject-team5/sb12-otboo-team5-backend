package com.codeit.otboo.api.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class WeatherRepositoryTest {
    private final WeatherGridRepository grids = mock(WeatherGridRepository.class);
    private final WeatherObservationRepository observations = mock(WeatherObservationRepository.class);
    private final WeatherForecastRepository forecasts = mock(WeatherForecastRepository.class);
    private final WeatherRepository repository = new WeatherRepository(grids, observations, forecasts);

    @Test
    void fillsMissingNamesUnderLockWithoutOverwritingCompleteNames() {
        var id = java.util.UUID.randomUUID();
        var grid = WeatherGrid.builder().id(id).nx(60).ny(127).build();
        when(grids.findByIdForUpdate(id)).thenReturn(Optional.of(grid));
        assertThat(repository.fillGridLocationNames(id, List.of("서울", "중구"))).isSameAs(grid);
        assertThat(grid.getLocationNames()).containsExactly("서울", "중구", "", "");
        repository.fillGridLocationNames(id, List.of("다른 지역"));
        repository.fillGridLocationNames(id, List.of());
        assertThat(grid.getLocationNames()).containsExactly("서울", "중구", "", "");
    }

    @Test
    void createsEnabledGridWithRegionNames() {
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.empty());
        when(grids.saveAndFlush(any(WeatherGrid.class))).thenAnswer(call -> call.getArgument(0));

        WeatherGrid result = repository.findOrCreateGrid(60, 127, List.of("서울특별시", "중구"));

        assertThat(result.getNx()).isEqualTo(60);
        assertThat(result.getNy()).isEqualTo(127);
        assertThat(result.getLocationNames()).containsExactly("서울특별시", "중구", "", "");
        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getLastRequestedAt()).isNotNull();
    }

    @Test
    void preservesExistingRegionNames() {
        WeatherGrid grid = WeatherGrid.create(60, 127, List.of("서울특별시", "중구"));
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.of(grid));

        assertThat(repository.findOrCreateGrid(60, 127, List.of())).isSameAs(grid);
        assertThat(grid.getLocationNames()).containsExactly("서울특별시", "중구", "", "");
        verify(grids, never()).saveAndFlush(any());
    }

    @Test
    void readsConcurrentInsertAfterFailedSave() {
        WeatherGrid winner = WeatherGrid.create(60, 127, List.of("서울특별시"));
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(grids.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(repository.findOrCreateGrid(60, 127, List.of())).isSameAs(winner);
    }

    @Test
    void propagatesOtherIntegrityFailures() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("invalid data");
        when(grids.findByNxAndNy(60, 127)).thenReturn(Optional.empty());
        when(grids.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> repository.findOrCreateGrid(60, 127, List.of()))
                .isSameAs(failure);
    }

    // ── ootd 스냅샷(findById) ────────────────────────────────────────────

    private static final java.time.OffsetDateTime TODAY_NOON =
            java.time.OffsetDateTime.now(KmaTimeCalculator.KST)
                    .truncatedTo(java.time.temporal.ChronoUnit.DAYS).plusHours(12);

    private WeatherForecast forecast(java.time.OffsetDateTime at, String temperature,
                                     String min, String max) {
        return WeatherForecast.builder()
                .id(java.util.UUID.randomUUID())
                .grid(GRID)
                .forecastedAt(at.minusHours(3))
                .forecastAt(at)
                .temperature(temperature == null ? null : new java.math.BigDecimal(temperature))
                .minTemperature(min == null ? null : new java.math.BigDecimal(min))
                .maxTemperature(max == null ? null : new java.math.BigDecimal(max))
                .precipitationType(PrecipitationType.RAIN)
                .skyStatus(SkyStatus.CLOUDY)
                .build();
    }

    private static final WeatherGrid GRID = WeatherGrid.builder()
            .id(java.util.UUID.randomUUID()).nx(60).ny(127).build();

    @Test
    void returnsNothingWhenForecastIsMissing() {
        var id = java.util.UUID.randomUUID();
        when(forecasts.findById(id)).thenReturn(Optional.empty());

        assertThat(repository.findById(id)).isEmpty();
    }

    // 기온 없는 스냅샷은 의미가 없다. ootd의 기온 컬럼도 NOT NULL이라 만들면 저장에서 터진다.
    @Test
    void returnsNothingWhenTemperatureIsMissing() {
        var forecast = forecast(TODAY_NOON, null, "10", "20");
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));

        assertThat(repository.findById(forecast.getId())).isEmpty();
    }

    @Test
    void keepsStoredMinAndMax() {
        var forecast = forecast(TODAY_NOON, "18", "10", "25");
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));

        var info = repository.findById(forecast.getId()).orElseThrow();

        assertThat(info.temperatureMin()).isEqualByComparingTo("10");
        assertThat(info.temperatureMax()).isEqualByComparingTo("25");
        // 강수 집계를 위해 최저·최고온도가 있어도 당일 데이터는 조회합니다.
        verify(forecasts).findRange(any(), any(), any());
    }

    // 단기예보는 최저/최고를 하루 중 특정 슬롯에만 담아 준다. 다른 슬롯을 집으면 비어 있다.
    @Test
    void derivesMinAndMaxFromTheWholeDayWhenMissing() {
        var forecast = forecast(TODAY_NOON, "18", null, null);
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));
        when(forecasts.findRange(any(), any(), any())).thenReturn(List.of(
                forecast(TODAY_NOON.minusHours(9), "11", null, null),
                forecast, forecast(TODAY_NOON.plusHours(3), "26", null, null)));

        var info = repository.findById(forecast.getId()).orElseThrow();

        assertThat(info.temperatureMin()).isEqualByComparingTo("11");
        assertThat(info.temperatureMax()).isEqualByComparingTo("26");
    }

    // 오늘 예보는 어제 "실제로 관측된" 값과 비교한다.
    @Test
    void comparesTodayAgainstYesterdayObservation() {
        var forecast = forecast(TODAY_NOON, "18", "10", "25");
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));
        when(observations.findByGrid_IdAndObservedAt(GRID.getId(), TODAY_NOON.minusDays(1)))
                .thenReturn(Optional.of(WeatherObservation.builder()
                        .grid(GRID).observedAt(TODAY_NOON.minusDays(1))
                        .temperature(new java.math.BigDecimal("15")).build()));

        var info = repository.findById(forecast.getId()).orElseThrow();

        assertThat(info.temperatureComparedToDayBefore()).isEqualByComparingTo("3");
        verify(forecasts, never()).findByGrid_IdAndForecastAtIn(any(), any());
    }

    // 미래 날짜는 관측값이 있을 수 없으므로 하루 전 예보와 비교한다.
    @Test
    void comparesFutureDayAgainstPreviousDayForecast() {
        var at = TODAY_NOON.plusDays(2);
        var forecast = forecast(at, "18", "10", "25");
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));
        when(forecasts.findRange(any(), any(), any()))
                .thenReturn(List.of(forecast), List.of(forecast(at.minusDays(1), "20", null, null)));

        var info = repository.findById(forecast.getId()).orElseThrow();

        assertThat(info.temperatureComparedToDayBefore()).isEqualByComparingTo("-2");
        verify(observations, never()).findByGrid_IdAndObservedAt(any(), any());
    }

    // 비교 대상이 없으면 비워 둔다. ootd에서 유일하게 NULL을 허용하는 컬럼이다.
    @Test
    void leavesComparisonEmptyWithoutBaseline() {
        var forecast = forecast(TODAY_NOON, "18", "10", "25");
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));
        when(observations.findByGrid_IdAndObservedAt(any(), any())).thenReturn(Optional.empty());

        assertThat(repository.findById(forecast.getId()).orElseThrow()
                .temperatureComparedToDayBefore()).isNull();
    }

    // 강수량과 강수확률은 0이 정상값이다. ootd에서 NOT NULL이므로 비워 보내면 저장에서 터진다.
    @Test
    void fillsMissingPrecipitationWithZero() {
        var forecast = forecast(TODAY_NOON, "18", "10", "25");
        when(forecasts.findById(forecast.getId())).thenReturn(Optional.of(forecast));

        var info = repository.findById(forecast.getId()).orElseThrow();

        assertThat(info.precipitationAmount()).isEqualByComparingTo("0");
        assertThat(info.precipitationProbability()).isEqualByComparingTo("0");
        assertThat(info.precipitationType()).isEqualTo(PrecipitationType.RAIN);
        assertThat(info.skyStatus()).isEqualTo(SkyStatus.CLOUDY);
    }
    @Test
    void aggregatesRainAcrossDayEvenWhenSelectedSlotIsDry() {
        var current = forecast(TODAY_NOON, "18", "10", "25");
        org.springframework.test.util.ReflectionTestUtils.setField(current, "precipitationType", PrecipitationType.NONE);
        var rainy = WeatherForecast.builder().id(java.util.UUID.randomUUID()).grid(GRID)
            .forecastAt(TODAY_NOON.plusHours(3)).forecastedAt(TODAY_NOON.minusHours(3))
            .temperature(new java.math.BigDecimal("22"))
            .precipitationType(PrecipitationType.RAIN)
            .precipitationAmount(new java.math.BigDecimal("5"))
            .precipitationProbability(new java.math.BigDecimal("80")).build();
        when(forecasts.findById(current.getId())).thenReturn(Optional.of(current));
        when(forecasts.findRange(any(), any(), any())).thenReturn(List.of(current, rainy));
        var info = repository.findById(current.getId()).orElseThrow();
        assertThat(info.precipitationAmount()).isEqualByComparingTo("5");
        assertThat(info.precipitationProbability()).isEqualByComparingTo("80");
        assertThat(info.precipitationType()).isEqualTo(PrecipitationType.RAIN);
    }

    @Test
    void midnightOnlyLastDayUsesSamePreviousRepresentativeAsScreen() {
        var now = TODAY_NOON.plusMinutes(30);
        var day = TODAY_NOON.plusDays(4).withHour(0);
        var current = forecast(day, "18", null, null);
        var previousMidnight = forecast(day.minusDays(1), "10", null, null);
        var previousNoon = forecast(day.minusDays(1).withHour(12), "20", null, null);
        var previousEvening = forecast(day.minusDays(1).withHour(18), "30", null, null);
        var previous = List.of(previousMidnight, previousNoon, previousEvening);
        var all = List.of(previousMidnight, previousNoon, previousEvening, current);
        when(forecasts.findById(current.getId())).thenReturn(Optional.of(current));
        when(forecasts.findRange(any(), any(), any())).thenReturn(List.of(current), previous);
        try (var time = org.mockito.Mockito.mockStatic(java.time.OffsetDateTime.class,
                org.mockito.Mockito.CALLS_REAL_METHODS)) {
            time.when(() -> java.time.OffsetDateTime.now(KmaTimeCalculator.KST)).thenReturn(now);
            var info = repository.findById(current.getId()).orElseThrow();
            var screenRepo = mock(WeatherRepository.class);
            when(screenRepo.findForecasts(any(), any(), any())).thenReturn(all);
            var screen = new com.codeit.otboo.api.weather.service.WeatherViewCacheService(
                screenRepo, mock(com.codeit.otboo.support.weather.client.KmaClient.class));
            List<com.codeit.otboo.api.weather.dto.response.WeatherViewData> views =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(screen, "assemble",
                    com.codeit.otboo.api.weather.dto.response.WeatherGridDto.from(GRID),
                    now.withHour(15).withMinute(0));
            var view = views.stream().filter(value -> value.id().equals(current.getId())).findFirst().orElseThrow();
            assertThat(info.temperatureComparedToDayBefore()).isEqualByComparingTo("-2");
            assertThat(info.temperatureComparedToDayBefore()).isEqualTo(view.temperature().comparedToDayBefore());
            assertThat(info.temperatureMin()).isEqualTo(view.temperature().min());
            assertThat(info.temperatureMax()).isEqualTo(view.temperature().max());
            assertThat(info.precipitationType()).isEqualTo(view.precipitation().type());
            assertThat(info.precipitationAmount()).isEqualTo(view.precipitation().amount());
            assertThat(info.precipitationProbability()).isEqualTo(view.precipitation().probability());
        }
    }

}
