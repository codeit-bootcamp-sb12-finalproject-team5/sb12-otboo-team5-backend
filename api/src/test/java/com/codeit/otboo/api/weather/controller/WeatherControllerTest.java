package com.codeit.otboo.api.weather.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.otboo.api.common.exception.GlobalExceptionHandler;
import com.codeit.otboo.support.kakao.client.KakaoClient;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.api.weather.service.Impl.WeatherServiceImpl;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WeatherControllerTest {
    private final WeatherRepository repository = mock(WeatherRepository.class);
    private final KakaoClient kakao = mock(KakaoClient.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WeatherController(
                    new WeatherServiceImpl(new LocationService(repository, kakao))))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void locationEndpointReturnsCoordinatesAndFourRegionLevels() throws Exception {
        when(repository.findGrid(60, 127)).thenReturn(Optional.of(
                WeatherGrid.create(60, 127, List.of("서울특별시", "중구", "명동"))));

        mvc.perform(get("/api/weathers/location")
                        .param("longitude", "126.978").param("latitude", "37.5665"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.longitude").value(126.978))
                .andExpect(jsonPath("$.latitude").value(37.5665))
                .andExpect(jsonPath("$.x").value(60))
                .andExpect(jsonPath("$.y").value(127))
                .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"))
                .andExpect(jsonPath("$.locationNames[3]").value(""));
        verifyNoInteractions(kakao);
    }

    @Test
    void rejectsNonFiniteCoordinatesUsingExistingErrorResponse() throws Exception {
        mvc.perform(get("/api/weathers/location")
                        .param("longitude", "NaN").param("latitude", "37.5665"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"));
        verifyNoInteractions(repository, kakao);
    }
    @Test
    void rejectsMissingAndMalformedCoordinatesForBothEndpoints() throws Exception {
        for (String path : List.of("/api/weathers", "/api/weathers/location")) {
            mvc.perform(get(path).param("longitude", "127"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.latitude").exists());
            mvc.perform(get(path).param("longitude", "not-a-number").param("latitude", "37.5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.longitude").exists());
            mvc.perform(get(path).param("longitude", "181").param("latitude", "37.5"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.longitude").exists());
        }
        verifyNoInteractions(repository, kakao);
    }

    @Test
    void weatherEndpointAcceptsValidatedQueryRequest() throws Exception {
        mvc.perform(get("/api/weathers").param("longitude", "126.978").param("latitude", "37.5665"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .json("[]"));
        verifyNoInteractions(repository, kakao);
    }
}
