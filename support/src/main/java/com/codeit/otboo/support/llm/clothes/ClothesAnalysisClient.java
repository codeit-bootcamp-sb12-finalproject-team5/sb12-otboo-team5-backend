package com.codeit.otboo.support.llm.clothes;

import com.codeit.otboo.support.llm.clothes.dto.ClothesAnalysisResult;

public interface ClothesAnalysisClient {

    ClothesAnalysisResult analyze(String prompt);

}
