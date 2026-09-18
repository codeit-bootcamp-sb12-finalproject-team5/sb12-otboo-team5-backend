package com.codeit.otboo.api.recommendation.service;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.api.recommendation.ranking.ContentBasedClothesRanker;
import com.codeit.otboo.api.recommendation.ranking.RankedClothesCandidates;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationRankingService {

    private final RecommendationFilteringService recommendationFilteringService;
    private final ContentBasedClothesRanker contentBasedClothesRanker;
    private final ProfileRepository profileRepository;

    public RankedClothesCandidates rankOotd(UUID userId, UUID weatherId) {
        Profile profile = findProfile(userId);

        // rule-based filtering
        List<Clothes> filteredClothes = recommendationFilteringService.filterOotd(userId, weatherId, profile);

        // content-based ranking
        return contentBasedClothesRanker.rank(profile, RecommendationType.OOTD, filteredClothes);
    }

    public RankedClothesCandidates rankOutfit(UUID userId, UUID weatherId) {
        Profile profile = findProfile(userId);

        // rule-based filtering
        List<Clothes> filteredClothes = recommendationFilteringService.filterOutfit(userId, weatherId, profile);

        // content-based ranking
        return contentBasedClothesRanker.rank(profile, RecommendationType.OUTFIT, filteredClothes);
    }

    private Profile findProfile(UUID userId) {
        return profileRepository.findByUser_Id(userId).orElseThrow(ProfileException::profileNotFound);
    }
}
