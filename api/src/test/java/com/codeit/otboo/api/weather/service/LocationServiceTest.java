package com.codeit.otboo.api.weather.service;

import com.codeit.otboo.support.kakao.client.KakaoClient;
import com.codeit.otboo.support.kakao.dto.response.KakaoRegionDto;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationServiceTest {
    /** 신규 격자에만 Kakao H 행정동을 조회해 저장하는지 확인합니다. */
    @Test
    void storesKakaoAdministrativeRegionForNewGrid() {
        KakaoClient kakaoClient = mock(KakaoClient.class);
        WeatherRepository weatherRepository = mock(WeatherRepository.class);
        LocationService service = new LocationService(weatherRepository, kakaoClient);
        List<String> names = List.of("경기도", "용인시 처인구", "백암면", "");
        when(weatherRepository.findGrid(66, 117)).thenReturn(Optional.empty());
        when(kakaoClient.findAdministrativeRegion(127.338, 37.123213))
                .thenReturn(Optional.of(new KakaoRegionDto(
                        "H", "4146135000", "경기도 용인시 처인구 백암면",
                        "경기도", "용인시 처인구", "백암면", "",
                        127.37534847382017, 37.16347098996221)));
        when(weatherRepository.findOrCreateGrid(66, 117, names))
                .thenReturn(WeatherGrid.create(66, 117, names));

        WeatherGrid result = service.findOrCreate(66, 117, 127.338, 37.123213);

        assertThat(result.getLocationNames()).isEqualTo(names);
        verify(kakaoClient).findAdministrativeRegion(127.338, 37.123213);
        verify(weatherRepository).findOrCreateGrid(66, 117, names);
    }

    /** 기존 격자는 DB 행정동을 사용하고 Kakao 호출을 생략하는지 확인합니다. */
    @Test
    void reusesStoredGridWithoutKakaoCall() {
        KakaoClient kakaoClient = mock(KakaoClient.class);
        WeatherRepository weatherRepository = mock(WeatherRepository.class);
        LocationService service = new LocationService(weatherRepository, kakaoClient);
        List<String> names = List.of("경기도", "용인시 처인구", "백암면", "");
        WeatherGrid stored = WeatherGrid.create(66, 117, names);
        when(weatherRepository.findGrid(66, 117)).thenReturn(Optional.of(stored));

        WeatherGrid result = service.findOrCreate(66, 117, 127.338, 37.123213);

        assertThat(result).isSameAs(stored);
        verify(kakaoClient, never()).findAdministrativeRegion(anyDouble(), anyDouble());
    }
    @Test
    void storesEmptyNamesWhenKakaoIsUnavailable() {
        KakaoClient kakaoClient = mock(KakaoClient.class);
        WeatherRepository repository = mock(WeatherRepository.class);
        LocationService service = new LocationService(repository, kakaoClient);
        when(repository.findGrid(60, 127)).thenReturn(Optional.empty());
        when(kakaoClient.findAdministrativeRegion(126.978, 37.5665))
                .thenReturn(Optional.empty());
        when(repository.findOrCreateGrid(60, 127, List.of()))
                .thenReturn(WeatherGrid.create(60, 127, List.of()));

        WeatherGrid result = service.findOrCreate(60, 127, 126.978, 37.5665);

        assertThat(result.getLocationNames()).containsExactly("", "", "", "");
    }

    @Test
    void convertsReferenceCoordinates() {
        LocationService service = new LocationService(
                mock(WeatherRepository.class), mock(KakaoClient.class));
        assertThat(service.convert(126.9780, 37.5665))
                .isEqualTo(new com.codeit.otboo.domain.weather.dto.GridCoordinate(60, 127));
        assertThat(service.convert(127.338, 37.123213))
                .isEqualTo(new com.codeit.otboo.domain.weather.dto.GridCoordinate(66, 117));
        assertThat(service.convert(126.93101409447922, 37.64200814360857))
                .isEqualTo(new com.codeit.otboo.domain.weather.dto.GridCoordinate(59, 128));
    }

    @Test
    void rejectsInvalidCoordinatesBeforeRepositoryOrClientAccess() {
        WeatherRepository repository = mock(WeatherRepository.class);
        KakaoClient kakaoClient = mock(KakaoClient.class);
        LocationService service = new LocationService(repository, kakaoClient);
        double[][] invalid = {{Double.NaN, 37}, {127, Double.POSITIVE_INFINITY},
                {181, 37}, {-181, 37}, {127, 90}, {127, -90}};
        for (double[] coordinate : invalid) {
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> service.convert(coordinate[0], coordinate[1]))
                    .isInstanceOfSatisfying(
                            com.codeit.otboo.api.weather.exception.WeatherException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(
                                    com.codeit.otboo.domain.common.exception.ErrorCode.INVALID_LOCATION_INPUT));
        }
        org.mockito.Mockito.verifyNoInteractions(repository, kakaoClient);
    }
}
