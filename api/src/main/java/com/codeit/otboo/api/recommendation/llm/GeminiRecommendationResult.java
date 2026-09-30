package com.codeit.otboo.api.recommendation.llm;

public record GeminiRecommendationResult(
    LlmRecommendationResponse recommendation,
    String modelVersion,
    Usage usage
) {
    public record Usage(
        Integer promptTokenCount,
        Integer candidatesTokenCount,
        Integer totalTokenCount
    ) {
    }
}
