package com.codeit.otboo.api.weather.service;

import com.codeit.otboo.api.weather.dto.request.LocationReadRequest;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.dto.response.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.response.WeatherDto;
import com.codeit.otboo.api.weather.dto.response.WeatherGridDto;
import java.util.List;

public interface WeatherService {
    List<WeatherDto> findWeather(WeatherReadRequest request);

    WeatherAPILocation findLocation(LocationReadRequest request);

    WeatherGridDto findGrid(double longitude, double latitude);
}
