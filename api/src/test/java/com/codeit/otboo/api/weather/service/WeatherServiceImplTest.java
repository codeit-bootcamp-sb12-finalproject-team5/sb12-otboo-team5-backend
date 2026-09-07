package com.codeit.otboo.api.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codeit.otboo.api.weather.client.KakaoClient;
import com.codeit.otboo.api.weather.dto.WeatherAPILocation;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.api.weather.service.Impl.WeatherServiceImpl;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeatherServiceImplTest {

    @Test
    void returnsOriginalCoordinatesAndStoredGridWithoutSwappingAxes() {
        WeatherRepository repository = mock(WeatherRepository.class);
        KakaoClient kakaoClient = mock(KakaoClient.class);
        WeatherService service = new WeatherServiceImpl(new LocationService(repository, kakaoClient));
        List<String> names = List.of("서울특별시", "중구", "명동", "");
        when(repository.findGrid(60, 127))
                .thenReturn(Optional.of(WeatherGrid.create(60, 127, names)));

        WeatherAPILocation result = service.findLocation(126.978, 37.5665);

        assertThat(result).isEqualTo(new WeatherAPILocation(37.5665, 126.978, 60, 127, names));
        verifyNoInteractions(kakaoClient);
    }

    @Test
    void rejectsMissingCoordinates() {
        LocationService location = mock(LocationService.class);
        WeatherService service = new WeatherServiceImpl(location);
        assertThatThrownBy(() -> service.findLocation(null, 37.5))
                .isInstanceOf(WeatherException.class);
        assertThatThrownBy(() -> service.findLocation(127.0, null))
                .isInstanceOf(WeatherException.class);
        verifyNoInteractions(location);
    }
}
