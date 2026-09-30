package com.codeit.otboo.batch.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.task.SyncTaskExecutor;

@ExtendWith(OutputCaptureExtension.class)
class DailyWeatherNotificationSchedulerTest {
    private final JobLauncher launcher = mock(JobLauncher.class);
    private final Job job = mock(Job.class);
    private final DailyWeatherNotificationScheduler scheduler =
            new DailyWeatherNotificationScheduler(launcher, job, new SyncTaskExecutor());

    @Test
    void completedDateIsSkippedWithoutErrorOrRetry(CapturedOutput output) throws Exception {
        when(launcher.run(eq(job), any(JobParameters.class)))
                .thenThrow(new JobInstanceAlreadyCompleteException("complete"));
        scheduler.runDailyNotification();
        verify(launcher, times(1)).run(eq(job), any(JobParameters.class));
        assertThat(output.getOut()).contains("이미 완료된 날짜이므로 실행 생략").doesNotContain("ERROR");
    }

    @Test
    void runningDateIsSkippedWithoutErrorOrRetry(CapturedOutput output) throws Exception {
        when(launcher.run(eq(job), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("running"));
        scheduler.runDailyNotification();
        verify(launcher, times(1)).run(eq(job), any(JobParameters.class));
        assertThat(output.getOut()).contains("이미 실행 중이므로 실행 생략").doesNotContain("ERROR");
    }

    @Test
    void unexpectedFailureStillLogsError(CapturedOutput output) throws Exception {
        when(launcher.run(eq(job), any(JobParameters.class))).thenThrow(new IllegalStateException("unavailable"));
        scheduler.runDailyNotification();
        assertThat(output.getOut()).contains("ERROR", "배치 실행 실패", "unavailable");
    }
}
