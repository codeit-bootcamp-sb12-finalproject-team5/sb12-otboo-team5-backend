package com.codeit.otboo.domain.weather.repository;

import java.util.UUID;
import java.util.List;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codeit.otboo.domain.weather.entity.WeatherForecast;

public interface WeatherForecastRepository extends JpaRepository<WeatherForecast, UUID> {
    @Query("select f from WeatherForecast f where f.grid.id = :gridId "
        + "and f.forecastAt >= :from and f.forecastAt < :to order by f.forecastAt")
    List<WeatherForecast> findRange(@Param("gridId") UUID gridId,
        @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    List<WeatherForecast> findByGrid_IdAndForecastAtIn(UUID gridId, List<OffsetDateTime> times);
}
