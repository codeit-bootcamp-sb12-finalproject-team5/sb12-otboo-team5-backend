package com.codeit.otboo.api.weather.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeit.otboo.api.weather.dto.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.WeatherDto;
import com.codeit.otboo.api.weather.service.WeatherService;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {
	private final WeatherService weatherService;

	@GetMapping
	public List<WeatherDto> getWeather(
		@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
		@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude) {
		return weatherService.findWeather(longitude, latitude);
	}

	@GetMapping("/location")
	public WeatherAPILocation getLocation(
		@RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
		@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude) {
		return weatherService.findLocation(longitude, latitude);
	}
}
