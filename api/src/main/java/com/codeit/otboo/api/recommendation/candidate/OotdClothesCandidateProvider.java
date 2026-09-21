package com.codeit.otboo.api.recommendation.candidate;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OotdClothesCandidateProvider implements ClothesCandidateProvider {

    private final ClothesRepository clothesRepository;

    @Override
    public RecommendationType getType() {
        return RecommendationType.OOTD;
    }

    @Override
    public List<Clothes> findCandidates(UUID userId) {
        return clothesRepository.findOwnedCandidatesByUserId(userId);
    }
}
