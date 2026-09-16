package com.codeit.otboo.api.recommendation;

import com.codeit.otboo.api.recommendation.dto.UserPreferenceRequest;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ProfileRepository profileRepository;
    private final ClothesAnalysisService clothesAnalysisService;

    @Transactional
    public void initializePreferenceVector(UUID userId, UserPreferenceRequest request) {
        Profile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(ProfileException::notFound);

        float[] preferenceVector = clothesAnalysisService.embed(request.toEmbeddingText());
        profile.updatePreferenceVector(preferenceVector);
    }
}
