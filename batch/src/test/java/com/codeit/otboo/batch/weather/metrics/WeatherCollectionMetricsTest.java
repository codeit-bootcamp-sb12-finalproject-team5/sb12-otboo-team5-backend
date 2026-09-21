package com.codeit.otboo.batch.weather.metrics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codeit.otboo.batch.weather.processor.NormalizedGridResult;
import com.codeit.otboo.batch.weather.processor.WeatherDataCountException;
import com.codeit.otboo.batch.weather.reader.GridCollectionResult;
import com.codeit.otboo.batch.weather.writer.WeatherJdbcItemWriter;
import com.codeit.otboo.domain.weather.entity.WeatherObservation;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class WeatherCollectionMetricsTest {

    private static final String PREFIX = "otboo.weather.collection.";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final WeatherCollectionMetrics metrics = new WeatherCollectionMetrics(registry);
    private final ResourcelessJobRepository repository = new ResourcelessJobRepository();

    @Test
    void registeredListenersMeasureRealJobAndChunkExecution() throws Exception {
        var first = grid(0);
        var missing = grid(1);
        var last = grid(2);
        var step = step("metricsStep", new ListItemReader<>(List.of(first, missing, last)),
                item -> {
                    if (item == missing) throw countException();
                    return normalized();
                },
                chunk -> {});

        var execution = launch(new JobBuilder("metricsJob", repository).start(step).listener(metrics).build());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(counter("read")).isEqualTo(3);
        // missing이 skip되면 청크를 다시 처리하므로 first는 Processor를 두 번 지나지만 한 번만 센다.
        assertThat(counter("processed")).isEqualTo(2);
        assertThat(counter("skipped")).isEqualTo(1);
        assertThat(registry.get(PREFIX + "chunk.duration").tag("outcome", "success").timer().count()).isPositive();
        assertThat(lastSuccess()).isPositive();
        // 메트릭용 값을 DB에 저장되는 Job 컨텍스트에 넣지 않는다.
        assertThat(execution.getExecutionContext().entrySet().stream().map(Map.Entry::getKey))
                .noneMatch(key -> key.startsWith("weatherMetrics"));
    }

    @Test
    void failedJobDoesNotReplaceLastSuccess() {
        metrics.afterJob(executionWithStatus(1L, BatchStatus.COMPLETED));
        double lastSuccess = lastSuccess();
        assertThat(lastSuccess).isPositive();

        metrics.afterJob(executionWithStatus(2L, BatchStatus.FAILED));

        assertThat(lastSuccess()).isEqualTo(lastSuccess);
    }

    // Job 소요 시간·실패 횟수는 spring.batch.job이 기록하므로 직접 만들지 않는다.
    @Test
    void jobDurationAndFailureCountAreLeftToSpringBatch() {
        assertThat(registry.find(PREFIX + "job.duration").timer()).isNull();
        assertThat(registry.find(PREFIX + "failed.jobs").counter()).isNull();
    }

    @Test
    void failureBeforeMetricsListenerStillRunsEarlierListeners() throws Exception {
        List<String> calls = new ArrayList<>();
        JobExecutionListener cacheEvict = new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                calls.add("cacheEvict");
            }
        };
        JobExecutionListener failingBefore = new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                throw new IllegalStateException("before");
            }
        };
        var step = step("orderStep", new ListItemReader<>(List.of()), item -> normalized(), chunk -> {});

        var execution = launch(new JobBuilder("orderJob", repository).start(step)
                .listener(cacheEvict).listener(failingBefore).listener(metrics).build());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(calls).containsExactly("cacheEvict");
        assertThat(lastSuccess()).isZero();
    }

    @Test
    void metricErrorsDoNotEscapeJobAndStepListeners() {
        var job = mock(JobExecution.class);
        when(job.getStatus()).thenThrow(new IllegalStateException("broken"));
        var step = mock(StepExecution.class);
        when(step.getWriteCount()).thenThrow(new IllegalStateException("broken"));

        assertThatCode(() -> metrics.afterJob(job)).doesNotThrowAnyException();
        assertThatCode(() -> metrics.afterStep(step)).doesNotThrowAnyException();
    }

    // process/write 실패는 Spring Batch 기본 지표로 확인하고, 기본 지표가 놓치는 읽기 실패만 직접 센다.
    // Spring Batch 업그레이드로 이 동작이 바뀌면 이 테스트가 실패한다. 그때 직접 세는 지표를 다시 정한다.
    @Test
    void springBatchCountsProcessAndWriteFailuresButNotReadFailures() throws Exception {
        var builtIn = new SimpleMeterRegistry();
        Metrics.addRegistry(builtIn);
        try {
            launch(new JobBuilder("processFailureJob", repository).start(step("processFailure",
                    new ListItemReader<>(List.of(grid(0))), item -> { throw countException(); }, chunk -> {})).build());
            launch(new JobBuilder("writeFailureJob", repository).start(step("writeFailure",
                    new ListItemReader<>(List.of(grid(0))), item -> normalized(),
                    chunk -> { throw new IllegalStateException("jdbc"); })).build());
            launch(new JobBuilder("readFailureJob", repository).start(step("readFailure",
                    () -> { throw new IllegalStateException("db"); }, item -> normalized(), chunk -> {})).build());

            assertThat(builtInCount(builtIn, "spring.batch.item.process", "processFailure", "FAILURE")).isPositive();
            assertThat(builtInCount(builtIn, "spring.batch.chunk.write", "writeFailure", "FAILURE")).isEqualTo(1);
            assertThat(builtInCount(builtIn, "spring.batch.item.read", "readFailure", "FAILURE")).isZero();
            assertThat(builtInCount(builtIn, "spring.batch.item.read", "readFailure", "SUCCESS")).isEqualTo(1);
            assertThat(registry.get(PREFIX + "failures").tag("phase", "read").counter().count()).isEqualTo(1);
            assertThat(registry.find(PREFIX + "failures").tag("phase", "process").counter()).isNull();
            assertThat(registry.find(PREFIX + "failures").tag("phase", "write").counter()).isNull();
        } finally {
            Metrics.removeRegistry(builtIn);
        }
    }

    @Test
    void communicationRecordsEmptyAndErrorsWithoutChangingOutcome() {
        assertThat(metrics.timeCommunication("forecast", Optional::empty)).isEmpty();
        var error = new IllegalStateException("network");
        assertThatThrownBy(() -> metrics.timeCommunication("forecast", () -> { throw error; })).isSameAs(error);
        for (String outcome : List.of("empty", "error")) {
            assertThat(registry.get(PREFIX + "communication.duration")
                    .tags("operation", "forecast", "outcome", outcome).timer().count()).isEqualTo(1);
        }
    }

    @Test
    void databaseCountsExcludeUnchangedRowsAndExposeUnknownCountsSeparately() {
        metrics.recordSaved("forecast", new int[]{1, 0, 1, Statement.SUCCESS_NO_INFO});
        assertThat(registry.get(PREFIX + "saved.rows").tag("type", "forecast").counter().count()).isEqualTo(2);
        assertThat(registry.get(PREFIX + "saved.unknown").tag("type", "forecast").counter().count()).isEqualTo(1);
    }

    @Test
    void writerCountsRowsOnlyAfterCommitAndNotOnRollback() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class))).thenReturn(new int[]{1});
        var writer = new WeatherJdbcItemWriter(jdbc, metrics);
        var item = new NormalizedGridResult(null, List.of(mock(WeatherObservation.class)), List.of(), false);
        for (boolean commit : List.of(false, true)) {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                writer.write(new Chunk<>(item));
                assertThat(registry.get(PREFIX + "saved.rows").tag("type", "observation").counter().count()).isZero();
                for (var callback : TransactionSynchronizationManager.getSynchronizations()) {
                    if (commit) callback.afterCommit();
                    callback.afterCompletion(commit ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK);
                }
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
        assertThat(registry.get(PREFIX + "saved.rows").tag("type", "observation").counter().count()).isEqualTo(1);
        assertThat(writer.getWrittenGridCount()).isEqualTo(1);
    }

    // 운영 weatherSyncStep과 같은 fault-tolerant 구성이다.
    private Step step(String name, ItemReader<GridCollectionResult> reader,
            ItemProcessor<GridCollectionResult, NormalizedGridResult> processor,
            ItemWriter<NormalizedGridResult> writer) {
        return new StepBuilder(name, repository)
                .<GridCollectionResult, NormalizedGridResult>chunk(10, new ResourcelessTransactionManager())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(WeatherDataCountException.class).skipLimit(10)
                .listener((Object) metrics)
                .build();
    }

    private JobExecution launch(Job job) throws Exception {
        var launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(repository);
        launcher.afterPropertiesSet();
        return launcher.run(job, new JobParameters());
    }

    private JobExecution executionWithStatus(long id, BatchStatus status) {
        var execution = new JobExecution(id);
        execution.setStatus(status);
        return execution;
    }

    private double counter(String name) {
        return registry.get(PREFIX + name).counter().count();
    }

    private double lastSuccess() {
        return registry.get(PREFIX + "last.success.timestamp").gauge().value();
    }

    // Spring Batch 5.2 기본 지표의 태그 키는 "지표 이름 + .status / .step.name" 형태다.
    private double builtInCount(SimpleMeterRegistry builtIn, String name, String stepName, String status) {
        Timer timer = builtIn.find(name).tag(name + ".step.name", stepName).tag(name + ".status", status).timer();
        return timer == null ? 0 : timer.count();
    }

    private static GridCollectionResult grid(int existingObservationCount) {
        return new GridCollectionResult(null, existingObservationCount, List.of(), Optional.empty());
    }

    private static NormalizedGridResult normalized() {
        return new NormalizedGridResult(null, List.of(), List.of(), false);
    }

    private static WeatherDataCountException countException() {
        return new WeatherDataCountException(UUID.randomUUID(), 60, 127);
    }
}
