package com.codeit.otboo.api.recommendation.candidate;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutfitClothesCandidateProvider implements ClothesCandidateProvider {

    private final ClothesRepository clothesRepository;

    @Override
    public RecommendationType getType() {
        return RecommendationType.OUTFIT;
    }

    @Override
    public List<Clothes> findCandidates(UUID userId) {
        return clothesRepository.findOutfitCandidates();
    }
}
