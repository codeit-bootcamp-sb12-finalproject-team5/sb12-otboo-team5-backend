package com.codeit.otboo.batch.weather.scheduler;

import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeeklyWeatherCleanupScheduler {
    private final JobLauncher jobLauncher;
    private final Job cleanupJob;

    public WeeklyWeatherCleanupScheduler(JobLauncher jobLauncher,
            @Qualifier("weeklyWeatherCleanupJob") Job cleanupJob) {
        this.jobLauncher = jobLauncher;
        this.cleanupJob = cleanupJob;
    }

    @Scheduled(cron = "0 0 3 * * THU", zone = "Asia/Seoul")
    public void runWeeklyCleanup() {
        try {
            var parameters = new JobParametersBuilder()
                .addString("cleanupDate", LocalDate.now(KmaTimeCalculator.KST).toString())
                .toJobParameters();
            jobLauncher.run(cleanupJob, parameters);
        } catch (Exception exception) {
            log.error("[BATCH][CLEANUP] 주간 날씨 정리 실행 실패", exception);
        }
    }
}
