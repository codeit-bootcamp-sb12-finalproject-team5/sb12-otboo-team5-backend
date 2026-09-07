package com.codeit.otboo.domain.weather.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codeit.otboo.domain.weather.entity.WeatherObservation;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, UUID> {
}
