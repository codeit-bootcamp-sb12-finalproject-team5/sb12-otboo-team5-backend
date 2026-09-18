package com.codeit.otboo.batch.weather.metrics;

import com.codeit.otboo.batch.weather.reader.GridCollectionResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterChunk;
import org.springframework.batch.core.annotation.AfterChunkError;
import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.batch.core.annotation.AfterRead;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeChunk;
import org.springframework.batch.core.annotation.OnReadError;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

// Spring Batch 기본 지표(spring.batch.*)가 주지 않는 값만 기록한다.
// Job 소요 시간·최종 상태는 spring.batch.job으로 확인한다.
// process/write 실패는 spring.batch.item.process, spring.batch.chunk.write의 status="FAILURE"로 확인한다.
@Slf4j
@Component
public class WeatherCollectionMetrics {

    private static final String PREFIX = "otboo.weather.collection.";
    private static final String CHUNK_START = "weatherMetricsChunkStartNanos";
    private final MeterRegistry registry;
    private final AtomicLong lastSuccess = new AtomicLong();

    public WeatherCollectionMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge(PREFIX + "last.success.timestamp", lastSuccess);
        for (String metric : new String[]{"read", "processed", "skipped"}) {
            registry.counter(PREFIX + metric);
        }
        for (String type : new String[]{"observation", "forecast"}) {
            registry.counter(PREFIX + "fetched.rows", "type", type);
            registry.counter(PREFIX + "saved.rows", "type", type);
            registry.counter(PREFIX + "saved.unknown", "type", type);
        }
        for (String outcome : new String[]{"success", "error"}) {
            registry.timer(PREFIX + "chunk.duration", "outcome", outcome);
        }
        for (String operation : new String[]{"observation", "forecast"}) {
            for (String outcome : new String[]{"success", "empty", "error"}) {
                registry.timer(PREFIX + "communication.duration", "operation", operation, "outcome", outcome);
            }
        }
        // Spring Batch 5.2는 읽기 실패도 spring.batch.item.read status="SUCCESS"로 남긴다.
        registry.counter(PREFIX + "failures", "phase", "read");
    }

    @AfterJob
    public void afterJob(JobExecution execution) {
        recordSafely("job", () -> {
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                lastSuccess.set(Instant.now().getEpochSecond());
            }
        });
    }

    @BeforeChunk
    public void beforeChunk(ChunkContext context) {
        context.setAttribute(CHUNK_START, System.nanoTime());
    }

    @AfterChunk
    public void afterChunk(ChunkContext context) {
        recordChunk(context, "success");
    }

    @AfterChunkError
    public void afterChunkError(ChunkContext context) {
        recordChunk(context, "error");
    }

    private void recordChunk(ChunkContext context, String outcome) {
        Object start = context.removeAttribute(CHUNK_START);
        if (start instanceof Long nanos) {
            registry.timer(PREFIX + "chunk.duration", "outcome", outcome)
                    .record(System.nanoTime() - nanos, TimeUnit.NANOSECONDS);
        }
    }

    // skip 뒤 청크를 다시 처리해도 읽기는 다시 하지 않으므로 격자당 한 번만 불린다.
    @AfterRead
    public void afterRead(GridCollectionResult item) {
        registry.counter(PREFIX + "read").increment();
        registry.counter(PREFIX + "fetched.rows", "type", "observation")
                .increment(item.fetchedObservations().size());
        registry.counter(PREFIX + "fetched.rows", "type", "forecast")
                .increment(item.forecastBundle().map(bundle -> bundle.points().size()).orElse(0));
    }

    @OnReadError
    public void onReadError(Exception exception) {
        registry.counter(PREFIX + "failures", "phase", "read").increment();
    }

    // 처리 건수는 롤백된 청크를 뺀 StepExecution 값으로 센다.
    // @AfterProcess는 skip 뒤 청크를 다시 처리할 때마다 불려 같은 격자를 여러 번 센다.
    @AfterStep
    public void afterStep(StepExecution execution) {
        recordSafely("step", () -> {
            registry.counter(PREFIX + "processed").increment(execution.getWriteCount() + execution.getFilterCount());
            registry.counter(PREFIX + "skipped").increment(execution.getSkipCount());
        });
    }

    // Spring Batch는 afterJob/afterStep 리스너를 등록 역순으로, 개별 예외 처리 없이 호출한다.
    // 메트릭 기록이 실패해도 캐시 무효화·실행 이력 저장 리스너는 실행되어야 한다.
    private void recordSafely(String target, Runnable recording) {
        try {
            recording.run();
        } catch (RuntimeException exception) {
            log.warn("[BATCH][METRICS] {} 메트릭 기록 실패", target, exception);
        }
    }

    // JDBC의 실제 영향 행 수만 집계한다. -2(SUCCESS_NO_INFO)는 별도로 표시한다.
    public void recordSaved(String type, int[] results) {
        for (int result : results) {
            if (result > 0) {
                registry.counter(PREFIX + "saved.rows", "type", type).increment(result);
            } else if (result == Statement.SUCCESS_NO_INFO) {
                registry.counter(PREFIX + "saved.unknown", "type", type).increment();
            }
        }
    }

    // 한 KmaClient 호출의 전체 대기 시간이며 내부 재시도/페이지 요청도 포함한다.
    public <T> Optional<T> timeCommunication(String operation, Supplier<Optional<T>> call) {
        long start = System.nanoTime();
        String outcome = "error";
        try {
            Optional<T> result = call.get();
            outcome = result.isPresent() ? "success" : "empty";
            return result;
        } finally {
            registry.timer(PREFIX + "communication.duration", "operation", operation, "outcome", outcome)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
