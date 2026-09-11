package com.codeit.otboo.batch.weather.config;

import com.codeit.otboo.batch.weather.processor.NormalizedGridResult;
import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.batch.weather.processor.WeatherDataCountException;
import com.codeit.otboo.batch.weather.processor.WeatherDataProcessor;
import com.codeit.otboo.batch.weather.reader.GridCollectionResult;
import com.codeit.otboo.batch.weather.service.WeatherBatchExecutionService;
import com.codeit.otboo.batch.weather.writer.WeatherJdbcItemWriter;
import com.codeit.otboo.support.common.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WeatherBatchJobConfig {

    private static final int CHUNK_SIZE = 10;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ItemReader<GridCollectionResult> weatherGridItemReader;
    private final WeatherDataProcessor weatherDataProcessor;
    private final WeatherJdbcItemWriter weatherJdbcItemWriter;
    private final WeatherSyncStepListener weatherSyncStepListener;
    private final CacheManager cacheManager;

    @Bean
    public Job dailyWeatherSyncJob() {
        return new JobBuilder(WeatherBatchExecutionService.JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .validator(parameters -> {
                    if (parameters.getString("collectionAt") == null) return;
                    try {
                        WeatherCollectionWindow.collectionAt(parameters.getString("collectionAt"));
                    } catch (BatchException exception) {
                        // JobLauncher의 파라미터 검증 계약은 유지합니다.
                        var invalid = new JobParametersInvalidException(exception.getErrorCode().getMessage());
                        invalid.initCause(exception);
                        throw invalid;
                    }
                })
                .start(weatherSyncStep())
                .listener(weatherViewCacheEvictListener())
                .build();
    }

    @Bean
    public Step weatherSyncStep() {
        return new StepBuilder("weatherSyncStep", jobRepository)
                .<GridCollectionResult, NormalizedGridResult>chunk(CHUNK_SIZE, transactionManager)
                // 수집 중 DB 트랜잭션을 열지 않습니다. Writer가 별도 트랜잭션으로 저장합니다.
                // 저장 후 체크포인트 실패 시 같은 항목이 재처리되므로 UPSERT를 유지합니다.
                .transactionAttribute(new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
                .reader(weatherGridItemReader)
                .processor(weatherDataProcessor)
                .writer(weatherJdbcItemWriter)
                .faultTolerant()
                .skip(WeatherDataCountException.class)
                .skipLimit(Integer.MAX_VALUE)
                .listener(weatherSyncStepListener)
                .build();
    }

    @Bean
    public JobExecutionListener weatherViewCacheEvictListener() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                // 실패한 Job에도 이미 커밋된 청크가 있을 수 있습니다.
                try {
                    Cache cache = cacheManager.getCache(CacheConfig.WEATHER_CACHE);
                    if (cache != null) {
                        cache.clear();
                        log.info("[BATCH] {} 캐시 전체 무효화 완료", CacheConfig.WEATHER_CACHE);
                    }
                } catch (RuntimeException exception) {
                    log.error("[BATCH] 캐시 무효화 실패: TTL 만료 전까지 이전 응답이 남을 수 있습니다.", exception);
                }
            }
        };
    }
}
