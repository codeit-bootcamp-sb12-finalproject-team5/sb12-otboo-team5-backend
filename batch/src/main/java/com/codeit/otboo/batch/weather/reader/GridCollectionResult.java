package com.codeit.otboo.batch.weather.reader;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import java.util.List;
import java.util.Optional;

public record GridCollectionResult(
    WeatherGrid grid,
    int existingObservationCount,
    List<KmaObservationDto> fetchedObservations,
    Optional<KmaForecastBundleDto> forecastBundle
) {
}
