package com.codeit.otboo.batch.weather.processor;

import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import java.util.List;

public record NormalizedGridResult(
    WeatherGrid grid,
    List<WeatherObservation> observations,
    List<WeatherForecast> forecasts,
    boolean partial
) {
}
