package com.codeit.otboo.batch.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.batch.notification.config.WeatherNotificationJobConfig;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@EnabledIfEnvironmentVariable(named = "TEST_NOTIFICATION_DB_URL", matches = ".+")
class WeatherNotificationJobIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private DriverManagerDataSource dataSource;
    private String schema;
    private JdbcTemplate jdbc;

    @BeforeEach
    void start() {
        dataSource = new DriverManagerDataSource(System.getenv("TEST_NOTIFICATION_DB_URL"), "notification_test", "notification_test");
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("notification_test");
        schema = "notification_batch_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        dataSource.setSchema(schema);
        new ResourceDatabasePopulator(new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql"))
                .execute(dataSource);
        jdbc.execute("CREATE TABLE weather_grid(id UUID PRIMARY KEY, nx INT, ny INT, enabled BOOLEAN, region_1depth TEXT, region_2depth TEXT, region_3depth TEXT, region_4depth TEXT)");
        jdbc.execute("CREATE TABLE users(id UUID PRIMARY KEY, deleted_at TIMESTAMPTZ)");
        jdbc.execute("CREATE TABLE profile(user_id UUID, weather_grid_id UUID)");
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("notification.kafka.enabled", "true")));
        context.registerBean("dataSource", DataSource.class, () -> dataSource);
        context.register(Config.class, WeatherNotificationJobConfig.class);
        context.refresh();
        when(context.getBean(KmaClient.class).findDailyNotificationForecast(any(), anyInt(), anyInt())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            OffsetDateTime base = call.getArgument(0);
            var points = new ArrayList<KmaForecastPointDto>();
            for (int hour = 0; hour < 24; hour++) {
                var time = base.plusDays(1).withHour(hour);
                points.add(new KmaForecastPointDto(time, "TMP", "20"));
                points.add(new KmaForecastPointDto(time, "PTY", "0"));
                points.add(new KmaForecastPointDto(time, "SKY", "1"));
            }
            return Optional.of(new KmaForecastBundleDto(base, call.getArgument(1), call.getArgument(2), points));
        });
        when(context.getBean(NotificationEventPublisher.class).publishCreate(anyString(), any()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return CompletableFuture.completedFuture(null);
                });
    }

    @AfterEach
    void stop() {
        if (context != null) context.close();
        if (jdbc != null && schema != null) jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
    }

    private void grid(int number, boolean enabled, boolean deleted, boolean profile) {
        var id = new UUID(0, number);
        jdbc.update("INSERT INTO weather_grid VALUES (?, 60, 127, ?, '서울시', '은평구', '진관동', '')", id, enabled);
        jdbc.update("INSERT INTO users VALUES (?, ?)", id, deleted ? OffsetDateTime.now() : null);
        if (profile) jdbc.update("INSERT INTO profile VALUES (?, ?)", id, id);
    }

    private JobExecution launch() throws Exception {
        return context.getBean(JobLauncher.class).run(context.getBean("dailyWeatherNotificationJob", Job.class),
                new JobParametersBuilder().addString("collectionDate", LocalDate.now(ZoneOffset.ofHours(9)).toString()).toJobParameters());
    }

    @Test
    void pagesGridsFiltersMissingDataAndPublishesOutsideTransaction() throws Exception {
        for (int i = 1; i <= 101; i++) grid(i, true, false, true);
        grid(102, false, false, true);
        grid(103, true, true, true);
        grid(104, true, false, false);
        jdbc.update("UPDATE weather_grid SET region_1depth='', region_2depth='', region_3depth='' WHERE id=?", new UUID(0, 50));
        var execution = launch();
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var step = execution.getStepExecutions().iterator().next();
        assertThat(step.getReadCount()).isEqualTo(101);
        assertThat(step.getFilterCount()).isEqualTo(1);
        assertThat(step.getWriteCount()).isEqualTo(100);
        verify(context.getBean(NotificationEventPublisher.class), times(100)).publishCreate(anyString(), any());
    }

    @Test
    void failedPublishFailsStepAndRestartResumesAfterCommittedGrid() throws Exception {
        for (int i = 1; i <= 3; i++) grid(i, true, false, true);
        var publisher = context.getBean(NotificationEventPublisher.class);
        when(publisher.publishCreate(eq(new UUID(0, 2).toString()), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
        assertThat(launch().getStatus()).isEqualTo(BatchStatus.FAILED);
        when(publisher.publishCreate(eq(new UUID(0, 2).toString()), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        assertThat(launch().getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(publisher, times(1)).publishCreate(eq(new UUID(0, 1).toString()), any());
        verify(publisher, times(2)).publishCreate(eq(new UUID(0, 2).toString()), any());
        verify(publisher, times(1)).publishCreate(eq(new UUID(0, 3).toString()), any());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableBatchProcessing
    static class Config {
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean KmaClient kmaClient() { return mock(KmaClient.class); }
        @Bean @Primary NotificationEventPublisher testPublisher() { return mock(NotificationEventPublisher.class); }
    }
}
