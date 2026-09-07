package com.codeit.otboo.domain.weather.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeit.otboo.domain.weather.entity.WeatherForecast;

@Repository
public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, UUID> {
}
