package com.codeit.otboo.api.recommendation.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesUsageCount;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PreferenceVectorAsyncServiceTest {

    private final ClothesAnalysisService clothesAnalysisService = mock(ClothesAnalysisService.class);
    private final ClothesRepository clothesRepository = mock(ClothesRepository.class);
    private final OutfitClothesRepository outfitClothesRepository = mock(OutfitClothesRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final PreferenceVectorAsyncService service =
        new PreferenceVectorAsyncService(
            clothesAnalysisService,
            clothesRepository,
            outfitClothesRepository,
            profileRepository
        );

    @Test
    void initializesPreferenceVectorWithSurveyAndWeightedClothesContributions() {
        UUID userId = UUID.randomUUID();
        UUID firstClothesId = UUID.randomUUID();
        UUID secondClothesId = UUID.randomUUID();
        float[] surveyVector = vector(1.0f, 2.0f);
        Clothes firstClothes = clothes(firstClothesId, 5, vector(3.0f, 4.0f));
        Clothes secondClothes = clothes(secondClothesId, 3, vector(5.0f, 6.0f));
        OutfitClothesUsageCount firstUsageCount = usageCount(firstClothesId, 1);
        OutfitClothesUsageCount secondUsageCount = usageCount(secondClothesId, 20);
        Profile profile = mock(Profile.class);

        when(clothesAnalysisService.embed("survey")).thenReturn(surveyVector);
        when(clothesRepository.findAllByUser_IdAndDeletedAtIsNull(userId))
            .thenReturn(List.of(firstClothes, secondClothes));
        when(outfitClothesRepository.countActiveOutfitUsageByUserId(userId))
            .thenReturn(List.of(firstUsageCount, secondUsageCount));
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        service.initialize(userId, "survey");

        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(profile).updatePreferenceVector(vectorCaptor.capture());

        float firstWeight = (float) (1.0 + 0.36 * Math.log(2.0));
        float secondWeight = (float) (0.25 + 0.36 * Math.log(21.0));
        assertThat(vectorCaptor.getValue()).hasSize(1536);
        assertThat(vectorCaptor.getValue()[0]).isCloseTo(
            3.0f + firstWeight * 3.0f + secondWeight * 5.0f,
            org.assertj.core.data.Offset.offset(0.0001f)
        );
        assertThat(vectorCaptor.getValue()[1]).isCloseTo(
            6.0f + firstWeight * 4.0f + secondWeight * 6.0f,
            org.assertj.core.data.Offset.offset(0.0001f)
        );
    }

    @Test
    void addsClothesContributionToPreferenceVector() {
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        Profile profile = profile(vector(10.0f, 20.0f));
        when(outfitClothesRepository.countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId))
            .thenReturn(1L);
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        service.addClothesContribution(userId, clothesId, 5, vector(3.0f, 4.0f));

        float clothesWeight = (float) (1.0 + 0.36 * Math.log(2.0));
        assertUpdatedVector(profile, 10.0f + clothesWeight * 3.0f, 20.0f + clothesWeight * 4.0f);
    }

    @Test
    void removesClothesContributionFromPreferenceVector() {
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        Profile profile = profile(vector(10.0f, 20.0f));
        when(outfitClothesRepository.countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId))
            .thenReturn(1L);
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        service.removeClothesContribution(userId, clothesId, 5, vector(3.0f, 4.0f));

        float clothesWeight = (float) (1.0 + 0.36 * Math.log(2.0));
        assertUpdatedVector(profile, 10.0f - clothesWeight * 3.0f, 20.0f - clothesWeight * 4.0f);
    }

    @Test
    void replacesOnlyPreferenceWeightWhenPreferenceChanges() {
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        Profile profile = profile(vector(10.0f, 20.0f));
        when(outfitClothesRepository.countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId))
            .thenReturn(20L);
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        service.updateClothesPreferenceContribution(userId, clothesId, 3, 5, vector(3.0f, 4.0f));

        assertUpdatedVector(profile, 12.25f, 23.0f);
    }

    @Test
    void addsOnlyFrequencyWeightDifferenceWhenOutfitUsageIncreases() {
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        Profile profile = profile(vector(10.0f, 20.0f));
        when(outfitClothesRepository.countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId))
            .thenReturn(1L);
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        service.increaseOutfitUsageContributions(
            userId,
            List.of(new ClothesContributionSnapshot(clothesId, 5, vector(3.0f, 4.0f)))
        );

        float frequencyWeightDifference = (float) (0.36 * Math.log(2.0));
        assertUpdatedVector(
            profile,
            10.0f + frequencyWeightDifference * 3.0f,
            20.0f + frequencyWeightDifference * 4.0f
        );
    }

    @Test
    void removesOnlyFrequencyWeightDifferenceWhenOutfitUsageDecreases() {
        UUID userId = UUID.randomUUID();
        UUID clothesId = UUID.randomUUID();
        Profile profile = profile(vector(10.0f, 20.0f));
        when(outfitClothesRepository.countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId))
            .thenReturn(0L);
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(profile));

        service.decreaseOutfitUsageContributions(
            userId,
            List.of(new ClothesContributionSnapshot(clothesId, 5, vector(3.0f, 4.0f)))
        );

        float frequencyWeightDifference = (float) (0.36 * Math.log(2.0));
        assertUpdatedVector(
            profile,
            10.0f - frequencyWeightDifference * 3.0f,
            20.0f - frequencyWeightDifference * 4.0f
        );
    }

    private Clothes clothes(UUID clothesId, int preference, float[] attributeVector) {
        Clothes clothes = mock(Clothes.class);
        when(clothes.getId()).thenReturn(clothesId);
        when(clothes.getPreference()).thenReturn(preference);
        when(clothes.getAttributeVector()).thenReturn(attributeVector);
        return clothes;
    }

    private OutfitClothesUsageCount usageCount(UUID clothesId, long usageCount) {
        OutfitClothesUsageCount result = mock(OutfitClothesUsageCount.class);
        when(result.getClothesId()).thenReturn(clothesId);
        when(result.getUsageCount()).thenReturn(usageCount);
        return result;
    }

    private Profile profile(float[] preferenceVector) {
        Profile profile = mock(Profile.class);
        when(profile.getPreferenceVector()).thenReturn(preferenceVector);
        return profile;
    }

    private void assertUpdatedVector(Profile profile, float expectedFirst, float expectedSecond) {
        ArgumentCaptor<float[]> vectorCaptor = ArgumentCaptor.forClass(float[].class);
        verify(profile).updatePreferenceVector(vectorCaptor.capture());
        assertThat(vectorCaptor.getValue()[0]).isCloseTo(
            expectedFirst,
            org.assertj.core.data.Offset.offset(0.0001f)
        );
        assertThat(vectorCaptor.getValue()[1]).isCloseTo(
            expectedSecond,
            org.assertj.core.data.Offset.offset(0.0001f)
        );
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1536];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}
