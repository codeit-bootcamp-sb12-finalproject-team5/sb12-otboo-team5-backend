package com.codeit.otboo.api.clothes;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmbeddingAsyncService {
    private final ClothesRepository clothesRepository;
    private final ClothesAnalysisService clothesEmbeddingService;

    @Async("taskExecutor")
    @Transactional
    public void clothesEmbedding(
            UUID clothesId,
            String attributeText
    ) {
        Float[] vector =
                clothesEmbeddingService.embed(attributeText);
        Clothes clothes =
                clothesRepository.findById(clothesId)
                        .orElseThrow(() ->
                                new ClothesException(
                                        ErrorCode.CLOTHES_NOT_FOUND
                                )
                        );
        clothes.setAttributeVector(vector);
    }
}
