package com.codeit.otboo.api.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.api.common.config.JpaAuditingConfig;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.service.Impl.WeatherServiceImpl;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.domain.weather.repository.WeatherObservationRepository;
import com.codeit.otboo.support.weather.client.KakaoClient;
import com.codeit.otboo.support.weather.client.KmaClient;
import com.codeit.otboo.support.weather.dto.response.KmaForecastBundleDto;
import com.codeit.otboo.support.weather.dto.response.KmaForecastPointDto;
import com.codeit.otboo.support.weather.dto.response.KmaObservationDto;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 지정된 PostgreSQL에 임시 스키마를 만들고 제거하며 실제 JPA 저장을 검증합니다. */
@EnabledIfEnvironmentVariable(named = "WEATHER_DB_TEST_URL", matches = ".+")
class WeatherRepositoryIntegrationTest {
    private static final String SCHEMA = "weather_test_" + UUID.randomUUID().toString().replace("-", "");
    private static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void start() throws Exception {
        execute("CREATE SCHEMA " + SCHEMA);
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stop() throws Exception {
        try {
            if (context != null) context.close();
        } finally {
            execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private static void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv("WEATHER_DB_TEST_URL"),
                System.getenv("WEATHER_DB_TEST_USER"), System.getenv("WEATHER_DB_TEST_PASSWORD"));
                var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    void serviceMissSavesAuditedEntitiesAndSecondRequestUsesDatabase() {
        KmaClient kma = context.getBean(KmaClient.class);
        KakaoClient kakao = context.getBean(KakaoClient.class);
        reset(kma, kakao);
        OffsetDateTime base = KmaTimeCalculator.currentVillageBase();
        List<KmaForecastPointDto> points = new ArrayList<>();
        OffsetDateTime first = OffsetDateTime.now(KmaTimeCalculator.KST).toLocalDate()
            .atStartOfDay().atOffset(KmaTimeCalculator.KST);
        for (int hour = 0; hour < 72; hour += 3) {
            points.add(new KmaForecastPointDto(first.plusHours(hour), "TMP", "25"));
            points.add(new KmaForecastPointDto(first.plusHours(hour), "REH", "60"));
        }
        when(kma.findLatestVillageForecast(60, 127)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new KmaForecastBundleDto(base, 60, 127, points));
        });
        when(kma.findObservation(any(), eq(60), eq(127))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return Optional.of(new KmaObservationDto(call.getArgument(0), 60, 127,
                Map.of("T1H", "22", "REH", "65")));
        });
        WeatherServiceImpl service = context.getBean(WeatherServiceImpl.class);
        var request = new WeatherReadRequest(126.978, 37.5665);

        var firstResponse = service.findWeather(request);
        var secondResponse = service.findWeather(request);

        assertThat(firstResponse).isNotEmpty().isEqualTo(secondResponse);
        assertThat(firstResponse.get(0).id()).isNotNull();
        assertThat(firstResponse.get(0).temperature().comparedToDayBefore()).isEqualByComparingTo("3");
        verify(kma, times(1)).findLatestVillageForecast(60, 127);
        verify(kma, times(1)).findObservation(any(), eq(60), eq(127));
        var stored = context.getBean(WeatherForecastRepository.class).findById(firstResponse.get(0).id()).orElseThrow();
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
    }

    @Test
    void concurrentUpsertsKeepSingleRowAndNewestIssueAcrossOffsets() throws Exception {
        WeatherRepository repository = context.getBean(WeatherRepository.class);
        WeatherGrid grid = repository.findOrCreateGrid(61, 128, List.of("테스트"));
        OffsetDateTime at = OffsetDateTime.parse("2026-09-08T15:00:00+09:00");
        WeatherForecast older = WeatherForecast.builder().grid(grid).forecastAt(at)
            .forecastedAt(at.minusHours(4)).temperature(new BigDecimal("20")).build();
        WeatherForecast newer = WeatherForecast.builder().grid(grid).forecastAt(at.withOffsetSameInstant(ZoneOffset.UTC))
            .forecastedAt(at.minusHours(1)).temperature(new BigDecimal("25")).build();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> repository.upsertForecasts(List.of(older)));
            var second = pool.submit(() -> repository.upsertForecasts(List.of(newer)));
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        var rows = repository.findForecasts(grid.getId(), at, at.plusHours(3));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTemperature()).isEqualByComparingTo("25");
        assertThat(rows.get(0).getForecastedAt().toInstant()).isEqualTo(at.minusHours(1).toInstant());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
    @EnableJpaRepositories(basePackageClasses = WeatherGridRepository.class)
    @Import(JpaAuditingConfig.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("WEATHER_DB_TEST_URL"),
                System.getenv("WEATHER_DB_TEST_USER"), System.getenv("WEATHER_DB_TEST_PASSWORD"));
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.codeit.otboo.domain.weather.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.default_schema", SCHEMA));
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        WeatherRepository weatherRepository(WeatherGridRepository grids,
                WeatherObservationRepository observations, WeatherForecastRepository forecasts) {
            return new WeatherRepository(grids, observations, forecasts);
        }

        @Bean
        KakaoClient kakaoClient() { return mock(KakaoClient.class); }

        @Bean
        KmaClient kmaClient() { return mock(KmaClient.class); }

        @Bean
        LocationService locationService(WeatherRepository repository, KakaoClient kakao) {
            return new LocationService(repository, kakao);
        }

        @Bean
        WeatherServiceImpl weatherService(LocationService location, WeatherRepository repository, KmaClient kma) {
            return new WeatherServiceImpl(location, repository, kma);
        }
    }
}
