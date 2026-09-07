package com.codeit.otboo.api.weather.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.codeit.otboo.api.weather.dto.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.WeatherDto;

public interface WeatherService {
	public List<WeatherDto> findWeather(Double longitude, Double latitude);

	public WeatherAPILocation findLocation(Double longitude, Double latitude);
}
