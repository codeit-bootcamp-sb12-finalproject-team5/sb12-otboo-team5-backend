package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmRecommendationService {
    public static final String PROMPT_VERSION = "outfit-recommendation-v1";
    private static final String PROMPT_RESOURCE = "classpath:prompts/outfit-recommendation-v1.txt";

    private final GeminiClient geminiClient;
    private final LlmRecommendationRequestMapper requestMapper;
    private final ResourceLoader resourceLoader;

    public GeminiRecommendationResult generate(WeatherForecast weather, RankedClothesCandidates rankedCandidates) {
        LlmRecommendationRequest request = requestMapper.map(weather, rankedCandidates);

        if (request.candidates().isEmpty()) {
            throw new IllegalArgumentException("Gemini recommendation requires at least one candidate");
        }
        return geminiClient.generate(loadPrompt(), request);
    }

    private String loadPrompt() {
        try {
            return resourceLoader.getResource(PROMPT_RESOURCE).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load prompt: " + PROMPT_VERSION, exception);
        }
    }
}
