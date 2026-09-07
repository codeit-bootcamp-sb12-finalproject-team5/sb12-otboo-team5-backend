package com.codeit.otboo.api.weather.repository;

import com.codeit.otboo.api.weather.dto.WeatherDto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRepository extends JpaRepository<WeatherDto, String> {
}
