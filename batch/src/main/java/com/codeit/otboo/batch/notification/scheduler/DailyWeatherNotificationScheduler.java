package com.codeit.otboo.batch.notification.scheduler;

import java.time.LocalDate;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class DailyWeatherNotificationScheduler {
    private final JobLauncher launcher;
    private final Job job;
    private final TaskExecutor executor;

    public DailyWeatherNotificationScheduler(JobLauncher launcher,
            @Qualifier("dailyWeatherNotificationJob") Job job,
            @Qualifier("weatherNotificationExecutor") TaskExecutor executor) {
        this.launcher = launcher;
        this.job = job;
        this.executor = executor;
    }

    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Seoul")
    public void runDailyNotification() {
        var parameters = new JobParametersBuilder()
                .addString("collectionDate", LocalDate.now(ZoneOffset.ofHours(9)).toString())
                .toJobParameters();

        try {
            executor.execute(() -> {
                try {
                    launcher.run(job, parameters);
                } catch (JobInstanceAlreadyCompleteException exception) {
                    log.info("[WEATHER-NOTIFICATION] 이미 완료된 날짜이므로 실행 생략 parameters={}", parameters);
                } catch (JobExecutionAlreadyRunningException exception) {
                    log.info("[WEATHER-NOTIFICATION] 이미 실행 중이므로 실행 생략 parameters={}", parameters);
                } catch (Exception exception) {
                    log.error("[WEATHER-NOTIFICATION] 배치 실행 실패 parameters={}", parameters, exception);
                }
            });
        } catch (RuntimeException exception) {
            log.error("[WEATHER-NOTIFICATION] 실행기 제출 실패 parameters={}", parameters, exception);
        }
    }
}
