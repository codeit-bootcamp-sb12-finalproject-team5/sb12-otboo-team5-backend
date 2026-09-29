package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.weather.dto.WeatherInfoResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.recommendation.exception.RecommendationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmRecommendationService {
    public static final String PROMPT_VERSION = "outfit-recommendation-v1";
    private static final String PROMPT_RESOURCE = "classpath:prompts/outfit-recommendation-v1.txt";
    private static final Duration TOTAL_GENERATION_TIMEOUT = Duration.ofSeconds(20);

    private final GeminiClient geminiClient;
    private final LlmRecommendationRequestMapper requestMapper;
    private final ResourceLoader resourceLoader;
    private final LlmRecommendationValidator validator;

    public GeminiRecommendationResult generate(WeatherInfoResponse weather, RankedClothesCandidates rankedCandidates) {
        return generate(weather, rankedCandidates, Set.of());
    }

    public GeminiRecommendationResult generate(
        WeatherInfoResponse weather,
        RankedClothesCandidates rankedCandidates,
        Set<String> recentFingerprints
    ) {
        LlmRecommendationRequest request = requestMapper.map(weather, rankedCandidates);

        if (request.selectedClothes().isEmpty() && request.candidates().isEmpty()) {
            throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_FAILED);
        }

        GenerationDeadline deadline = GenerationDeadline.start(TOTAL_GENERATION_TIMEOUT);
        try {
            GeminiRecommendationResult result = generateOnce(request, null, deadline);
            LlmRecommendationValidator.ValidationResult validation = validator.validate(request, result.recommendation());

            if (validation.isValid()) {
                GeminiRecommendationResult filtered = removeRecentDuplicates(result, recentFingerprints);
                if (!filtered.recommendation().outfits().isEmpty()) return filtered;
            }
            ValidationFailureReason firstReason = validation.isValid()
                ? ValidationFailureReason.RECENT_HISTORY_DUPLICATE : validation.reason();
            logValidation(firstReason, 1);

            return validateRecoveryResult(request, recentFingerprints, deadline,
                correctionContext(firstReason) + historyCorrectionContext(recentFingerprints));

        } catch (RecommendationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // 타임아웃된 동일 요청을 다시 보내면 사용자 대기만 길어지므로 즉시 종료한다.
            if (geminiClient.isTimedOut(exception)) {
                throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_TIMEOUT, exception);
            }

            // 429·5xx·연결 실패에만 1회 회복 기회를 준다. 보정 생성과 합쳐 최대 2회만 호출한다.
            if (geminiClient.isRetryable(exception)) {
                try {
                    return validateRecoveryResult(request, recentFingerprints, deadline, null);
                } catch (RuntimeException recoveryException) {
                    throw toRecommendationException(recoveryException);
                }
            }
            throw toRecommendationException(exception);
        }
    }

    private GeminiRecommendationResult validateRecoveryResult(
        LlmRecommendationRequest request,
        Set<String> recentFingerprints,
        GenerationDeadline deadline,
        String correctionContext
    ) {
        GeminiRecommendationResult recovered = generateOnce(request, correctionContext, deadline);
        LlmRecommendationValidator.ValidationResult validation = validator.validate(request, recovered.recommendation());
        if (validation.isValid()) {
            GeminiRecommendationResult filtered = removeRecentDuplicates(recovered, recentFingerprints);
            if (!filtered.recommendation().outfits().isEmpty()) return filtered;
        }

        logValidation(validation.isValid() ? ValidationFailureReason.RECENT_HISTORY_DUPLICATE : validation.reason(), 2);
        throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_FAILED);
    }

    private GeminiRecommendationResult generateOnce(
        LlmRecommendationRequest request,
        String correctionContext,
        GenerationDeadline deadline
    ) {
        // 호출마다 10초를 넘기지 않되, 전체 20초 예산의 남은 시간보다 길게 기다리지 않는다.
        Duration timeout = deadline.nextRequestTimeout(geminiClient.requestTimeout());
        return geminiClient.generate(loadPrompt(), request, correctionContext, timeout);
    }

    /**
     * 외부 라이브러리 예외가 컨트롤러까지 새지 않도록 추천 도메인 예외로 한 곳에서 변환한다.
     * 이미 변환된 도메인 예외는 상태 코드와 상세 사유를 보존한다.
     */
    private RecommendationException toRecommendationException(RuntimeException exception) {
        if (exception instanceof RecommendationException recommendationException) {
            return recommendationException;
        }
        return new RecommendationException(
            geminiClient.isTimedOut(exception)
                ? ErrorCode.RECOMMENDATION_GENERATION_TIMEOUT
                : ErrorCode.RECOMMENDATION_GENERATION_FAILED,
            exception
        );
    }

    // 추천 요청 1건에 대해 외부 LLM 호출에 사용할 수 있는 총 대기 시간 관리
    private static final class GenerationDeadline {
        private final long deadlineNanos;

        private GenerationDeadline(Duration budget) {
            this.deadlineNanos = System.nanoTime() + budget.toNanos();
        }

        static GenerationDeadline start(Duration budget) {
            return new GenerationDeadline(budget);
        }

        Duration nextRequestTimeout(Duration maximumPerRequest) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_TIMEOUT);
            }
            return Duration.ofNanos(Math.min(remainingNanos, maximumPerRequest.toNanos()));
        }
    }

    private GeminiRecommendationResult removeRecentDuplicates(GeminiRecommendationResult result, Set<String> recentFingerprints) {
        return new GeminiRecommendationResult(
            validator.removeRecentDuplicateOutfits(result.recommendation(), recentFingerprints),
            result.modelVersion(), result.usage());
    }

    private String historyCorrectionContext(Set<String> recentFingerprints) {
        return recentFingerprints.isEmpty() ? "" : " 이미 추천한 Outfit fingerprint는 사용하지 마세요: "
            + String.join(", ", recentFingerprints) + ".";
    }

    private String loadPrompt() {
        try {
            return resourceLoader.getResource(PROMPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load prompt: " + PROMPT_VERSION, exception);
        }
    }

    private String correctionContext(ValidationFailureReason reason) {
        return switch (reason) {
            case UNKNOWN_CLOTHES_ID -> "직전 응답에 selectedClothes 또는 candidates에 없는 의상 ID가 포함되었습니다. 제공된 ID만 사용하세요.";
            case MISSING_SELECTED_CLOTHES -> "모든 Outfit에는 selectedClothes의 모든 ID가 예외 없이 포함되어야 합니다.";
            case INVALID_BASIC_OUTFIT, INVALID_OPTIONAL_COUNT -> "TOP과 정확히 하나의 BOTTOM을 조합하거나, DRESS 하나만 사용하세요. "
                + "선택 카테고리별 의상은 최대 하나만 사용하세요.";
            case RECENT_HISTORY_DUPLICATE -> "직전 응답은 이미 추천한 Outfit과 동일합니다. 서로 다른 유효한 Outfit을 생성하세요.";
            default -> "직전 응답이 구조화된 Outfit 규칙을 위반했습니다. 유효하고 서로 다른 Outfit을 다시 생성하세요.";
        };
    }

    private void logValidation(ValidationFailureReason reason, int attempt) {
        LoggerFactory.getLogger(getClass()).warn("[recommendation] Gemini validation failed. reason={}, attempt={}", reason, attempt);
    }
}
