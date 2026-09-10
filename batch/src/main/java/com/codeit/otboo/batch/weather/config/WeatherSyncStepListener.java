package com.codeit.otboo.batch.weather.config;

import com.codeit.otboo.batch.weather.service.WeatherBatchExecutionService;
import com.codeit.otboo.batch.weather.writer.WeatherJdbcItemWriter;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherSyncStepListener implements StepExecutionListener {

    private final WeatherBatchExecutionService executionService;
    private final WeatherGridRepository gridRepository;
    private final WeatherJdbcItemWriter writer;

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String collectionAt = stepExecution.getJobParameters().getString("collectionAt");
        if (collectionAt == null) {
            // 기존의 파라미터 없는 기동 실행도 한 번 정한 기준 시각을 공유합니다.
            collectionAt = stepExecution.getExecutionContext().getString("collectionAt",
                OffsetDateTime.now(KmaTimeCalculator.KST).toString());
        }
        stepExecution.getExecutionContext().putString("collectionAt", collectionAt);
        long totalGrids = gridRepository.countByEnabledTrue();
        stepExecution.getExecutionContext().putInt("totalGrids", Math.toIntExact(totalGrids));
        executionService.start(collectionDate(stepExecution), Math.toIntExact(totalGrids));
        log.info("[BATCH][LISTENER] Step 시작 totalGrids={}", totalGrids);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        LocalDate targetDate = collectionDate(stepExecution);
        int totalGrids = stepExecution.getExecutionContext().getInt("totalGrids", 0);
        int writtenGridCount = writer.getWrittenGridCount();
        int partialGridCount = writer.getPartialGridCount();
        int skipCount = (int) stepExecution.getSkipCount();

        boolean stepFailed = stepExecution.getStatus().isUnsuccessful();
        String status;
        String errorMessage = null;

        if (stepFailed) {
            status = "FAILED";
            errorMessage = stepExecution.getFailureExceptions().stream()
                    .findFirst()
                    .map(Throwable::getMessage)
                    .orElse("배치 Step이 실패했습니다.");
        } else if (partialGridCount > 0 || skipCount > 0) {
            status = "PARTIAL";
        } else {
            status = "COMPLETED";
        }

        int failedGridCount = Math.max(skipCount, (int) totalGrids - writtenGridCount);

        executionService.finish(targetDate, status, writtenGridCount, failedGridCount, errorMessage);

        log.info("[BATCH][LISTENER] Step 종료 status={}, totalGrids={}, written={}, partial={}, skip={}, failed={}",
                status, totalGrids, writtenGridCount, partialGridCount, skipCount, failedGridCount);

        return stepExecution.getExitStatus();
    }
    private LocalDate collectionDate(StepExecution stepExecution) {
        return WeatherCollectionWindow.collectionAt(
            stepExecution.getExecutionContext().getString("collectionAt")).toLocalDate();
    }
}
