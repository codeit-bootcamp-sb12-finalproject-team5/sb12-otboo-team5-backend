package com.codeit.otboo.domain.weather.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codeit.otboo.domain.weather.entity.WeatherForecast;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, UUID> {
}
