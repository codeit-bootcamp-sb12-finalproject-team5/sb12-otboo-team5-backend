package com.codeit.otboo.api.weather.controller;

import com.codeit.otboo.api.weather.dto.request.LocationReadRequest;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.dto.response.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.response.WeatherDto;
import com.codeit.otboo.api.weather.service.WeatherService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weatherService;

    @GetMapping
    public ResponseEntity<List<WeatherDto>> getWeather(
            @ModelAttribute @Valid WeatherReadRequest request) {
        return ResponseEntity.ok(weatherService.findWeather(request));
    }

    @GetMapping("/location")
    public ResponseEntity<WeatherAPILocation> getLocation(
            @ModelAttribute @Valid LocationReadRequest request) {
        return ResponseEntity.ok(weatherService.findLocation(request));
    }
}
