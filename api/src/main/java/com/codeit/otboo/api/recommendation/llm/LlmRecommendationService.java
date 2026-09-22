package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.weather.dto.WeatherInfoResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.recommendation.exception.RecommendationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

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
            throw new IllegalArgumentException("Gemini recommendation requires at least one candidate");
        }

        try {
            GeminiRecommendationResult result = geminiClient.generate(loadPrompt(), request);
            LlmRecommendationValidator.ValidationResult validation = validator.validate(request, result.recommendation());

            if (validation.isValid()) {
                GeminiRecommendationResult filtered = removeRecentDuplicates(result, recentFingerprints);
                if (!filtered.recommendation().outfits().isEmpty()) return filtered;
            }
            ValidationFailureReason firstReason = validation.isValid()
                ? ValidationFailureReason.RECENT_HISTORY_DUPLICATE : validation.reason();
            logValidation(firstReason, 1);

            GeminiRecommendationResult corrected = geminiClient.generate(loadPrompt(), request,
                correctionContext(firstReason) + historyCorrectionContext(recentFingerprints));
            LlmRecommendationValidator.ValidationResult correctedValidation = validator.validate(request, corrected.recommendation());

            if (correctedValidation.isValid()) {
                GeminiRecommendationResult filtered = removeRecentDuplicates(corrected, recentFingerprints);
                if (!filtered.recommendation().outfits().isEmpty()) return filtered;
            }
            logValidation(correctedValidation.isValid()
                ? ValidationFailureReason.RECENT_HISTORY_DUPLICATE : correctedValidation.reason(), 2);

            throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_FAILED, null);

        } catch (RecommendationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RecommendationException(ErrorCode.RECOMMENDATION_GENERATION_FAILED, exception);
        }
    }

    private GeminiRecommendationResult removeRecentDuplicates(GeminiRecommendationResult result, Set<String> recentFingerprints) {
        return new GeminiRecommendationResult(
            validator.removeRecentDuplicateOutfits(result.recommendation(), recentFingerprints),
            result.modelVersion(), result.usage());
    }

    private String historyCorrectionContext(Set<String> recentFingerprints) {
        return recentFingerprints.isEmpty() ? "" : " Do not use any previously recommended outfit fingerprint: "
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
            case UNKNOWN_CLOTHES_ID ->
                "The previous response contained an ID outside selectedClothes and candidates. Use only provided IDs.";
            case MISSING_SELECTED_CLOTHES ->
                "Every outfit must include every ID from selectedClothes without exception.";
            case INVALID_BASIC_OUTFIT, INVALID_OPTIONAL_COUNT ->
                "Use either TOP with exactly one BOTTOM, or exactly one DRESS. "
                    + "Use at most one item from each optional category.";
            case RECENT_HISTORY_DUPLICATE ->
                "The previous response reused an already recommended outfit. Generate different valid outfits.";
            default ->
                "The previous response violated the structured outfit rules. Regenerate valid, distinct outfits.";
        };
    }

    private void logValidation(ValidationFailureReason reason, int attempt) {
        LoggerFactory.getLogger(getClass()).warn("[recommendation] Gemini validation failed. reason={}, attempt={}", reason, attempt);
    }
}
