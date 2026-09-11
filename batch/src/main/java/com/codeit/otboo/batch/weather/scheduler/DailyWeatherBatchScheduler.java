package com.codeit.otboo.batch.weather.scheduler;

import java.time.LocalDate;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import org.springframework.beans.factory.annotation.Qualifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DailyWeatherBatchScheduler {

    private final JobLauncher jobLauncher;
    private final Job dailyWeatherSyncJob;

    public DailyWeatherBatchScheduler(JobLauncher jobLauncher,
            @Qualifier("dailyWeatherSyncJob") Job dailyWeatherSyncJob) {
        this.jobLauncher = jobLauncher;
        this.dailyWeatherSyncJob = dailyWeatherSyncJob;
    }

    @Scheduled(cron = "0 30 23 * * *", zone = "Asia/Seoul")
    public void runDailySync() {
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addString("collectionAt", LocalDate.now(KmaTimeCalculator.KST)
                            .atTime(23, 50).atOffset(KmaTimeCalculator.KST).toString())
                    .toJobParameters();
            jobLauncher.run(dailyWeatherSyncJob, parameters);
        } catch (Exception exception) {
            log.error("[BATCH][SCHEDULER] dailyWeatherSyncJob 실행 실패", exception);
        }
    }
}
