package com.codeit.otboo.domain.weather.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;

public interface WeatherGridRepository extends JpaRepository<WeatherGrid, UUID> {
    Optional<WeatherGrid> findByNxAndNy(int nx, int ny);
}
