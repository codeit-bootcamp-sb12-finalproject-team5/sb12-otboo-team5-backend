package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.exception.ClothesException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import com.codeit.otboo.support.openai.clothes.ClothesPromptBuilder;
import com.codeit.otboo.support.openai.clothes.OpenAiClientException;
import com.codeit.otboo.support.openai.clothes.OpenAiWebSearchClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesAnalysisService {

    private final ClothesPromptBuilder promptBuilder;
    private final OpenAiWebSearchClient webSearchClient;

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
}
