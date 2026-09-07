package com.codeit.otboo.api.weather.service.Impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.codeit.otboo.api.weather.dto.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.WeatherDto;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.api.weather.service.WeatherService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {
	private final LocationService locationService;

	@Override
	public List<WeatherDto> findWeather(Double longitude, Double latitude) {
		return List.of();
	}

	@Override
	public WeatherAPILocation findLocation(Double longitude, Double latitude) {
		lo
		return null;
	}
}
