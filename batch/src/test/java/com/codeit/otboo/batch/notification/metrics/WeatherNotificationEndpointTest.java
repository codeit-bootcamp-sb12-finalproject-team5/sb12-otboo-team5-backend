package com.codeit.otboo.batch.notification.metrics;

import com.codeit.otboo.batch.weather.metrics.WeatherCollectionMetrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.batch.BatchObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

@AutoConfigureObservability
@SpringBootTest(classes = WeatherNotificationEndpointTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeatherNotificationEndpointTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WeatherNotificationMetrics metrics;

    @Autowired
    private ResourcelessJobRepository jobRepository;

    @Autowired
    private Job endpointBeanJob;

    @Test
    void exposesCustomCounterThroughPrometheusEndpoint() {
        metrics.recordPublished();

        var response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(
                "otboo_weather_notification_published_total{application=\"batch\"} 1.0");
        assertThat(response.getBody()).contains("otboo_weather_collection_read_total",
                "otboo_weather_collection_last_success_timestamp");
        assertThat(restTemplate.getForEntity("/actuator/env", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // 날씨 수집 메트릭은 write 실패를 직접 세지 않는다. Spring Batch 기본 지표가 같은 endpoint로 나가야 한다.
    @Test
    void exposesSpringBatchFailureMetricsUsedInsteadOfCustomCounters() throws Exception {
        var repository = new ResourcelessJobRepository();
        var step = new StepBuilder("endpointWriteFailureStep", repository)
                .<String, String>chunk(10, new ResourcelessTransactionManager())
                .reader(new ListItemReader<>(List.of("grid")))
                .processor(item -> item)
                .writer(chunk -> {
                    throw new IllegalStateException("jdbc");
                })
                .build();
        var launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(repository);
        launcher.afterPropertiesSet();
        launcher.run(new JobBuilder("endpointWriteFailureJob", repository).start(step).build(), new JobParameters());

        var body = restTemplate.getForObject("/actuator/prometheus", String.class);

        assertThat(body.lines())
                .filteredOn(line -> line.startsWith("spring_batch_chunk_write_seconds_count"))
                .anyMatch(line -> line.contains("step_name=\"endpointWriteFailureStep\"")
                        && line.contains("status=\"FAILURE\""));
    }

    // 날씨 수집 메트릭은 Job 소요 시간·실패 횟수를 직접 세지 않는다.
    // 운영처럼 빈으로 등록된 Job에는 actuator가 관측을 연결하므로 spring_batch_job_seconds가 남아야 한다.
    @Test
    void exposesSpringBatchJobMetricsForJobBeans() throws Exception {
        var launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.afterPropertiesSet();
        launcher.run(endpointBeanJob, new JobParameters());

        var body = restTemplate.getForObject("/actuator/prometheus", String.class);

        assertThat(body.lines())
                .filteredOn(line -> line.startsWith("spring_batch_job_seconds_count"))
                .anyMatch(line -> line.contains("spring_batch_job_name=\"endpointBeanJob\"")
                        && line.contains("spring_batch_job_status=\"COMPLETED\""));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({WeatherNotificationMetrics.class, WeatherCollectionMetrics.class})
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            EndpointAutoConfiguration.class,
            WebEndpointAutoConfiguration.class,
            ManagementContextAutoConfiguration.class,
            MetricsAutoConfiguration.class,
            PrometheusMetricsExportAutoConfiguration.class,
            ObservationAutoConfiguration.class,
            BatchObservationAutoConfiguration.class
    })
    static class TestApplication {

        @Bean
        ResourcelessJobRepository jobRepository() {
            return new ResourcelessJobRepository();
        }

        @Bean
        Job endpointBeanJob(ResourcelessJobRepository jobRepository) {
            var step = new StepBuilder("endpointBeanStep", jobRepository)
                    .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, new ResourcelessTransactionManager())
                    .build();
            return new JobBuilder("endpointBeanJob", jobRepository).start(step).build();
        }
    }
}
