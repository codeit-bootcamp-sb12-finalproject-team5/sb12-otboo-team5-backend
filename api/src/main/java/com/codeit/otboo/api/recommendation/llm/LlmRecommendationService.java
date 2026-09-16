package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.recommendation.exception.RecommendationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

//todo: 예외처리 enum, 컨벤션에 통일되게 예외 날리기 필요
@Service
@RequiredArgsConstructor
public class LlmRecommendationService {
    public static final String PROMPT_VERSION = "outfit-recommendation-v1";
    private static final String PROMPT_RESOURCE = "classpath:prompts/outfit-recommendation-v1.txt";

    private final GeminiClient geminiClient;
    private final LlmRecommendationRequestMapper requestMapper;
    private final ResourceLoader resourceLoader;
    private final LlmRecommendationValidator validator;

    public GeminiRecommendationResult generate(WeatherForecast weather, RankedClothesCandidates rankedCandidates) {
        LlmRecommendationRequest request = requestMapper.map(weather, rankedCandidates);

        if (request.candidates().isEmpty()) {
            throw new IllegalArgumentException("Gemini recommendation requires at least one candidate");
        }

        try {
            GeminiRecommendationResult result = geminiClient.generate(loadPrompt(), request);
            LlmRecommendationValidator.ValidationResult validation = validator.validate(request, result.recommendation());

            if (validation.isValid()) return result;
            logValidation(validation.reason(), 1);

            GeminiRecommendationResult corrected = geminiClient.generate(loadPrompt(), request, correctionContext(validation.reason()));
            LlmRecommendationValidator.ValidationResult correctedValidation = validator.validate(request, corrected.recommendation());

            if (correctedValidation.isValid()) return corrected;
            logValidation(correctedValidation.reason(), 2);

            throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_FAILED, null);

        } catch (RecommendationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_FAILED, exception);
        }
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
            case UNKNOWN_CLOTHES_ID ->
                "The previous response contained an ID outside candidates. Use only provided candidate IDs.";
            case INVALID_TOP_COUNT, INVALID_BOTTOM_COUNT, INVALID_OUTER_COUNT, INVALID_SHOES_COUNT ->
                "Follow the outfit rule: at least one TOP, exactly one BOTTOM, at most one OUTER and one SHOES.";
            default ->
                "The previous response violated the structured outfit rules. Regenerate valid, distinct outfits.";
        };
    }

    private void logValidation(ValidationFailureReason reason, int attempt) {
        LoggerFactory.getLogger(getClass()).warn("[recommendation] Gemini validation failed. reason={}, attempt={}", reason, attempt);
    }
}
