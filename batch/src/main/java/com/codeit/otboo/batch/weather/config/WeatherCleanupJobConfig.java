package com.codeit.otboo.batch.weather.config;

import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.batch.weather.tasklet.WeatherCleanupTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class WeatherCleanupJobConfig {
    public static final String JOB_NAME = "weeklyWeatherCleanupJob";

    @Bean
    public Job weeklyWeatherCleanupJob(JobRepository jobRepository,
            @Qualifier("weatherCleanupStep") Step step) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .validator(parameters -> {
                try {
                    WeatherCleanupTasklet.parseCleanupDate(parameters.getString("cleanupDate"));
                } catch (BatchException exception) {
                    var invalid = new JobParametersInvalidException(exception.getErrorCode().getMessage());
                    invalid.initCause(exception);
                    throw invalid;
                }
            })
            .start(step)
            .build();
    }

    @Bean
    public Step weatherCleanupStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager, WeatherCleanupTasklet tasklet) {
        return new StepBuilder("weatherCleanupStep", jobRepository)
            .tasklet(tasklet, transactionManager)
            .build();
    }
}
