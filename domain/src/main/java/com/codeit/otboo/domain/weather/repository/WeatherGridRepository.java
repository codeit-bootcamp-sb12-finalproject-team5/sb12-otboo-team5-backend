package com.codeit.otboo.domain.weather.repository;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;

public interface WeatherGridRepository extends JpaRepository<WeatherGrid, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from WeatherGrid g where g.id = :id")
    Optional<WeatherGrid> findByIdForUpdate(@Param("id") UUID id);

    Optional<WeatherGrid> findByNxAndNy(int nx, int ny);
}
