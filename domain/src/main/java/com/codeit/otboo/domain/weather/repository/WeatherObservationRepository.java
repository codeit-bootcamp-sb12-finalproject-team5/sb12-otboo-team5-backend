package com.codeit.otboo.domain.weather.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.OffsetDateTime;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codeit.otboo.domain.weather.entity.WeatherObservation;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, UUID> {
    Optional<WeatherObservation> findByGrid_IdAndObservedAt(UUID gridId, OffsetDateTime observedAt);

    List<WeatherObservation> findByGrid_IdAndObservedAtIn(UUID gridId, List<OffsetDateTime> times);
}
