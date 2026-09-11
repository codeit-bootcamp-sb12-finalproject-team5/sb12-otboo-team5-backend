package com.codeit.otboo.domain.weather.repository;

import com.codeit.otboo.domain.weather.entity.WeatherBatchExecution;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherBatchExecutionRepository extends JpaRepository<WeatherBatchExecution, UUID> {
    Optional<WeatherBatchExecution> findByJobNameAndTargetDate(String jobName, LocalDate targetDate);
}
