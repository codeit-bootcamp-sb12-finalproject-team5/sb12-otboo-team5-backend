package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.recommendation.preference.PreferenceVectorAsyncService;
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
    private final PreferenceVectorAsyncService preferenceVectorAsyncService;

    @Async("taskExecutor")
    @Transactional
    public void createClothesEmbedding(
            UUID clothesId,
            String attributeText
    ) {
        Clothes clothes = embedAndSave(clothesId, attributeText);
        if (clothes.isDeleted()) {
            return;
        }

        preferenceVectorAsyncService.addClothesContribution(
            clothes.getUser().getId(),
            clothes.getId(),
            clothes.getPreference(),
            clothes.getAttributeVector().clone()
        );
    }

    @Async("taskExecutor")
    @Transactional
    public void updateClothesEmbedding(UUID clothesId, String attributeText) {
        embedAndSave(clothesId, attributeText);
    }

    private Clothes embedAndSave(UUID clothesId, String attributeText) {
        float[] vector = clothesEmbeddingService.embed(attributeText);
        Clothes clothes = clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.CLOTHES_NOT_FOUND));
        clothes.setAttributeVector(vector);
        return clothes;
    }
}
