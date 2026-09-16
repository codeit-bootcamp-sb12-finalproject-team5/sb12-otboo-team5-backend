package com.codeit.otboo.batch.notification.config;

import com.codeit.otboo.batch.notification.processor.WeatherNotificationProcessor;
import com.codeit.otboo.batch.notification.reader.WeatherNotificationGrid;
import com.codeit.otboo.batch.notification.writer.WeatherNotificationEventWriter;
import com.codeit.otboo.domain.notification.event.NotificationCreateMessage;
import com.codeit.otboo.domain.notification.event.WeatherNotificationCreateEvent;
import com.codeit.otboo.support.notification.kafka.NotificationEventPublisher;
import com.codeit.otboo.support.notification.kafka.NotificationKafkaConfig;
import com.codeit.otboo.support.weather.client.KmaClient;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
@Import(NotificationKafkaConfig.class)
public class WeatherNotificationJobConfig {

    @Bean
    public Job dailyWeatherNotificationJob(JobRepository repository,
            @Qualifier("weatherNotificationStep") Step step) {
        return new JobBuilder("dailyWeatherNotificationJob", repository)
                .validator(parameters -> {
                    try {
                        String value = parameters.getString("collectionDate");
                        if (value == null || !LocalDate.parse(value).toString().equals(value)) {
                            throw new IllegalArgumentException();
                        }
                    } catch (RuntimeException exception) {
                        throw new JobParametersInvalidException("collectionDate는 yyyy-MM-dd 형식이어야 합니다.");
                    }
                })
                .start(step).build();
    }

    @Bean
    public Step weatherNotificationStep(JobRepository repository, PlatformTransactionManager transactions,
            @Qualifier("weatherNotificationGridReader") JdbcPagingItemReader<WeatherNotificationGrid> reader,
            WeatherNotificationProcessor processor, WeatherNotificationEventWriter writer) {
        return new StepBuilder("weatherNotificationStep", repository)
                .<WeatherNotificationGrid, NotificationCreateMessage<WeatherNotificationCreateEvent>>chunk(1, transactions)
                .transactionAttribute(new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
                .reader(reader).processor(processor).writer(writer)
                .listener(new StepExecutionListener() {
                    @Override
                    public org.springframework.batch.core.ExitStatus afterStep(StepExecution execution) {
                        log.info("[WEATHER-NOTIFICATION] status={}, read={}, skipped={}, kafkaPublished={}, failures={}",
                                execution.getStatus(), execution.getReadCount(), execution.getFilterCount(),
                                execution.getWriteCount(), execution.getFailureExceptions().size());
                        return null;
                    }
                }).build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<WeatherNotificationGrid> weatherNotificationGridReader(DataSource dataSource) {
        var query = new PostgresPagingQueryProvider();

        query.setSelectClause("SELECT g.id, g.nx, g.ny, g.region_1depth, g.region_2depth, g.region_3depth, g.region_4depth");
        query.setFromClause("FROM weather_grid g");
        query.setWhereClause("""
                WHERE g.enabled = true AND EXISTS (
                    SELECT 1 FROM profile p JOIN users u ON u.id = p.user_id
                    WHERE p.weather_grid_id = g.id AND u.deleted_at IS NULL
                )
                """);
        query.setSortKeys(Map.of("id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<WeatherNotificationGrid>()
                .name("weatherNotificationGridReader").dataSource(dataSource)
                .queryProvider(query).pageSize(100).saveState(true)
                .rowMapper((rs, row) -> new WeatherNotificationGrid(rs.getObject("id", UUID.class),
                        rs.getInt("nx"), rs.getInt("ny"), Arrays.asList(rs.getString("region_1depth"),
                        rs.getString("region_2depth"), rs.getString("region_3depth"), rs.getString("region_4depth"))))
                .build();
    }

    @Bean
    @StepScope
    public WeatherNotificationProcessor weatherNotificationProcessor(KmaClient client,
            @Value("#{jobParameters['collectionDate']}") String collectionDate) {
        return new WeatherNotificationProcessor(client, LocalDate.parse(collectionDate), Clock.systemUTC());
    }

    @Bean
    public WeatherNotificationEventWriter weatherNotificationEventWriter(NotificationEventPublisher publisher) {
        return new WeatherNotificationEventWriter(publisher, Clock.systemUTC());
    }

    @Bean
    public ThreadPoolTaskExecutor weatherNotificationExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("weather-notification-");
        return executor;
    }
}
