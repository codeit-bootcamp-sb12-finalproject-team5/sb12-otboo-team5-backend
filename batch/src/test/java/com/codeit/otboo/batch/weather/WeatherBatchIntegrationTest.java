package com.codeit.otboo.batch.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.batch.common.config.JpaAuditingConfig;
import com.codeit.otboo.batch.weather.config.*;
import com.codeit.otboo.batch.weather.processor.WeatherDataProcessor;
import com.codeit.otboo.batch.weather.reader.WeatherGridItemReader;
import com.codeit.otboo.batch.weather.service.WeatherBatchExecutionService;
import com.codeit.otboo.batch.weather.writer.WeatherJdbcItemWriter;
import com.codeit.otboo.batch.weather.tasklet.WeatherCleanupTasklet;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherBatchExecutionRepository;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.*;
import jakarta.persistence.EntityManagerFactory;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@EnabledIfEnvironmentVariable(named = "WEATHER_DB_TEST_URL", matches = ".+")
class WeatherBatchIntegrationTest {
    private static final String SCHEMA = "batch_test_" + UUID.randomUUID().toString().replace("-", "");
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void start() throws Exception {
        execute("CREATE SCHEMA " + SCHEMA);
        var dataSource = dataSourceForTest();
        new ResourceDatabasePopulator(new ClassPathResource("org/springframework/batch/core/schema-postgresql.sql"))
            .execute(dataSource);
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stop() throws Exception {
        try { if (context != null) context.close(); }
        finally { execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE"); }
    }

    private static DriverManagerDataSource dataSourceForTest() {
        var ds = new DriverManagerDataSource(System.getenv("WEATHER_DB_TEST_URL"),
            System.getenv("WEATHER_DB_TEST_USER"), System.getenv("WEATHER_DB_TEST_PASSWORD"));
        ds.setSchema(SCHEMA);
        return ds;
    }

    private static void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("WEATHER_DB_TEST_URL"),
                System.getenv("WEATHER_DB_TEST_USER"), System.getenv("WEATHER_DB_TEST_PASSWORD"));
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @BeforeEach
    void prepare() {
        var jdbc = context.getBean(JdbcTemplate.class);
        reset(jdbc, context.getBean(KmaClient.class));
        jdbc.update("delete from weather_forecast");
        jdbc.update("delete from weather_observation");
        jdbc.update("delete from weather_grid");
        var grids = context.getBean(WeatherGridRepository.class);
        for (int i = 0; i < 11; i++) grids.saveAndFlush(WeatherGrid.create(60 + i, 127, List.of("테스트")));
        var kma = context.getBean(KmaClient.class);
        when(kma.findObservation(any(), anyInt(), anyInt())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new KmaObservationDto(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                Map.of("T1H", "22", "REH", "65")));
        });
        when(kma.findVillageForecast(any(), anyInt(), anyInt())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            OffsetDateTime base = call.getArgument(0);
            List<KmaForecastPointDto> points = new ArrayList<>();
            OffsetDateTime first = base.toLocalDate().plusDays(1).atStartOfDay().atOffset(base.getOffset());
            for (int hour = 0; hour < 24; hour += 3) {
                points.add(new KmaForecastPointDto(first.plusHours(hour), "TMP", "25"));
            }
            return Optional.of(new KmaForecastBundleDto(base, call.getArgument(1), call.getArgument(2), points));
        });
    }

    @Test
    void committedChunksCountOnceAndCollectionIsOutsideTransaction() throws Exception {
        var execution = launch("2026-09-08T23:50:00+09:00");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var stored = context.getBean(WeatherBatchExecutionRepository.class)
            .findByJobNameAndTargetDate("dailyWeatherSyncJob", java.time.LocalDate.of(2026, 9, 8)).orElseThrow();
        assertThat(stored.getSuccessGridCount()).isEqualTo(11);
        assertThat(stored.getFailedGridCount()).isZero();
        assertThat(stored.getStatus()).isEqualTo("COMPLETED");
        assertThat(count("weather_observation")).isEqualTo(88);
        assertThat(count("weather_forecast")).isEqualTo(88);
    }

    @Test
    void failedSecondChunkRollsBackRowsAndCountersAndStillClearsCache() throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("forced second chunk failure");
            return call.callRealMethod();
        }).when(jdbc).batchUpdate(contains("INSERT INTO weather_forecast"), any(BatchPreparedStatementSetter.class));
        var cache = context.getBean(CacheManager.class).getCache("weather");
        cache.put("old", "value");
        var execution = launch("2026-09-09T23:50:00+09:00");
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        var stored = context.getBean(WeatherBatchExecutionRepository.class)
            .findByJobNameAndTargetDate("dailyWeatherSyncJob", java.time.LocalDate.of(2026, 9, 9)).orElseThrow();
        assertThat(stored.getSuccessGridCount()).isEqualTo(10);
        assertThat(stored.getFailedGridCount()).isEqualTo(1);
        assertThat(count("weather_observation")).isEqualTo(80);
        assertThat(count("weather_forecast")).isEqualTo(80);
        assertThat(cache.get("old")).isNull();

        reset(jdbc);
        var kma = context.getBean(KmaClient.class);
        clearInvocations(kma);
        var restarted = launch("2026-09-09T23:50:00+09:00");
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getId()).isEqualTo(execution.getJobInstance().getId());
        assertThat(count("weather_observation")).isEqualTo(88);
        assertThat(count("weather_forecast")).isEqualTo(88);
        verify(kma, times(8)).findObservation(any(), anyInt(), anyInt());
        var completed = context.getBean(WeatherBatchExecutionRepository.class)
            .findByJobNameAndTargetDate("dailyWeatherSyncJob", java.time.LocalDate.of(2026, 9, 9)).orElseThrow();
        assertThat(completed.getSuccessGridCount()).isEqualTo(11);
        assertThat(completed.getFailedGridCount()).isZero();
    }

    @Test
    void cleanupDeletesOnlyBeforeCutoffsAndKeepsFutureForecastWithOldIssue() throws Exception {
        var day = java.time.LocalDate.of(2026, 9, 10);
        seedCleanupRows(day);
        var execution = launchCleanup(day);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(count("weather_observation")).isEqualTo(2);
        assertThat(count("weather_forecast")).isEqualTo(2);
        var state = execution.getStepExecutions().iterator().next().getExecutionContext();
        assertThat(state.getInt("deletedObservationCount")).isEqualTo(1);
        assertThat(state.getInt("deletedForecastCount")).isEqualTo(1);
        verifyNoInteractions(context.getBean(KmaClient.class));
    }

    @Test
    void cleanupRollsBackObservationDeletionWhenForecastDeletionFails() throws Exception {
        var day = java.time.LocalDate.of(2026, 9, 17);
        seedCleanupRows(day);
        var jdbc = context.getBean(JdbcTemplate.class);
        doThrow(new IllegalStateException("forced cleanup failure")).when(jdbc)
            .update(eq("DELETE FROM weather_forecast WHERE forecast_at < ?"), any(Object[].class));
        var execution = launchCleanup(day);
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(count("weather_observation")).isEqualTo(3);
        assertThat(count("weather_forecast")).isEqualTo(3);
    }

    private void seedCleanupRows(java.time.LocalDate day) {
        var jdbc = context.getBean(JdbcTemplate.class);
        UUID gridId = context.getBean(WeatherGridRepository.class).findAll().get(0).getId();
        var start = day.atStartOfDay().atOffset(java.time.ZoneOffset.ofHours(9));
        var observationCutoff = start.minusDays(2);
        for (var at : List.of(observationCutoff.minusSeconds(1), observationCutoff, observationCutoff.plusSeconds(1))) {
            jdbc.update("INSERT INTO weather_observation (id, grid_id, observed_at, created_at, updated_at) VALUES (?, ?, ?, now(), now())",
                UUID.randomUUID(), gridId, at.withOffsetSameInstant(java.time.ZoneOffset.UTC));
        }
        var forecastCutoff = start.minusDays(1);
        for (var at : List.of(forecastCutoff.minusSeconds(1), forecastCutoff, start.plusDays(1))) {
            jdbc.update("INSERT INTO weather_forecast (id, grid_id, forecasted_at, forecast_at, created_at, updated_at) VALUES (?, ?, ?, ?, now(), now())",
                UUID.randomUUID(), gridId, start.minusDays(5), at.withOffsetSameInstant(java.time.ZoneOffset.UTC));
        }
    }

    private JobExecution launchCleanup(java.time.LocalDate date) throws Exception {
        return context.getBean(JobLauncher.class).run(context.getBean("weeklyWeatherCleanupJob", Job.class),
            new JobParametersBuilder().addString("cleanupDate", date.toString()).toJobParameters());
    }

    private JobExecution launch(String at) throws Exception {
        return context.getBean(JobLauncher.class).run(context.getBean("dailyWeatherSyncJob", Job.class),
            new JobParametersBuilder().addString("collectionAt", at).toJobParameters());
    }

    private int count(String table) {
        return context.getBean(JdbcTemplate.class).queryForObject("select count(*) from " + table, Integer.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableBatchProcessing
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
    @EnableJpaRepositories(basePackageClasses = WeatherGridRepository.class)
    @Import({JpaAuditingConfig.class, WeatherBatchJobConfig.class, WeatherSyncStepListener.class,
        WeatherGridItemReader.class, WeatherDataProcessor.class, WeatherJdbcItemWriter.class,
        WeatherBatchExecutionService.class, WeatherCleanupJobConfig.class, WeatherCleanupTasklet.class})
    static class Config {
        @Bean DataSource dataSource() { return dataSourceForTest(); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return spy(new JdbcTemplate(ds)); }
        @Bean KmaClient kmaClient() { return mock(KmaClient.class); }
        @Bean CacheManager cacheManager() { return new ConcurrentMapCacheManager("weather"); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("com.codeit.otboo.domain.weather.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.default_schema", SCHEMA));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
