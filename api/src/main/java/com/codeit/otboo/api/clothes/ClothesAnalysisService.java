package com.codeit.otboo.api.clothes;

import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import com.codeit.otboo.support.openai.clothes.ClothesPromptBuilder;
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
        return webSearchClient.analyze(prompt);
    }
}
