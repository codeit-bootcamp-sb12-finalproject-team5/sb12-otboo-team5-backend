package com.codeit.otboo.batch.weather.service;

import com.codeit.otboo.domain.weather.entity.WeatherBatchExecution;
import com.codeit.otboo.domain.weather.repository.WeatherBatchExecutionRepository;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WeatherBatchExecutionService {

    public static final String JOB_NAME = "dailyWeatherSyncJob";

    private final WeatherBatchExecutionRepository repository;

    public void start(LocalDate targetDate, int totalGridCount) {
        OffsetDateTime now = OffsetDateTime.now(KmaTimeCalculator.KST);
        WeatherBatchExecution execution = repository.findByJobNameAndTargetDate(JOB_NAME, targetDate)
                .orElseGet(() -> repository.save(WeatherBatchExecution.builder()
                        .jobName(JOB_NAME)
                        .targetDate(targetDate)
                        .status("RUNNING")
                        .startedAt(now)
                        .build()));
        execution.restart(totalGridCount, now);
    }

    public void finish(
            LocalDate targetDate,
            String status,
            int successGridCount,
            int failedGridCount,
            String errorMessage) {
        repository.findByJobNameAndTargetDate(JOB_NAME, targetDate)
                .ifPresent(execution -> execution.complete(
                        status,
                        successGridCount,
                        failedGridCount,
                        OffsetDateTime.now(KmaTimeCalculator.KST),
                        errorMessage));
    }
}
