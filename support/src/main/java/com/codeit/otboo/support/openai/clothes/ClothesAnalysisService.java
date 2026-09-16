package com.codeit.otboo.support.openai.clothes;

import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.support.openai.clothes.exception.ClothesAnalysisClientException;
import com.codeit.otboo.support.openai.clothes.exception.ProductImageResolutionException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesAnalysisService {
    private final ClothesPromptBuilder promptBuilder;
    private final ClothesAnalysisClient webSearchClient;
    private final ProductImageResolver productImageResolver;
    private final EmbeddingModel embeddingModel;

    public ClothesAnalysisResult analyze(String url) {
        String prompt = promptBuilder.build(url);
        try {
            String imageUrl = productImageResolver.resolve(url);
            return webSearchClient.analyze(prompt)
                    .withImageUrl(imageUrl)
                    .withoutBracketedNameAndBrand();
        } catch (ClothesAnalysisClientException | ProductImageResolutionException e) {
            throw new ClothesException(
                ErrorCode.CLOTHES_ANALYSIS_FAILED,
                e
            );
        }
    }

    public float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector.length != 1536) {
            throw new IllegalStateException(
                    "Embedding dimension must be 1536, but was "
                            + vector.length
            );
        }
        return vector;
    }
}
