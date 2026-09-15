package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesAnalysisService {
    private final ClothesPromptBuilder promptBuilder;
    private final OpenAiWebSearchClient webSearchClient;
    private final EmbeddingModel embeddingModel;

    public ClothesAnalysisResult analyze(String url) {
        String prompt = promptBuilder.build(url);
        try {
            return webSearchClient.analyze(prompt);
        } catch (OpenAiClientException e) {
            throw new ClothesException(
                ErrorCode.CLOTHES_ANALYSIS_FAILED,
                e
            );
        }
    }

    public Float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector.length != 1536) {
            throw new IllegalStateException(
                    "Embedding dimension must be 1536, but was "
                            + vector.length
            );
        }
        Float[] result = new Float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i];
        }
        return result;
    }
}
