package com.codeit.otboo.api.recommendation.preference;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.profile.entity.Gender;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreferenceSurveyServiceTest {
    private final ClothesRepository clothesRepository = mock(ClothesRepository.class);
    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final S3StorageService storage = mock(S3StorageService.class);
    private final PreferenceVectorAsyncService vectors = mock(PreferenceVectorAsyncService.class);
    private final PreferenceSurveyService service = new PreferenceSurveyService(
        clothesRepository, profileRepository, storage, vectors);
    private final UUID userId = UUID.randomUUID();

    @Test
    void returnsNinePerCategoryForEachGender() {
        List<Clothes> candidates = new ArrayList<>();
        for (ClothesCategory category : ClothesCategory.values()) {
            for (int i = 0; i < 12; i++) {
                candidates.add(clothes(category, ClothesGender.BOTH, 1));
            }
        }
        when(clothesRepository.findSurveyCandidates()).thenReturn(candidates);
        for (Gender gender : List.of(Gender.MALE, Gender.FEMALE)) {
            profile(gender);
            var options = service.getOptions(userId);
            assertThat(options).hasSize(gender == Gender.MALE ? 36 : 45);
            var counts = options.stream().collect(java.util.stream.Collectors.groupingBy(
                option -> option.category(), java.util.stream.Collectors.counting()));
            assertThat(counts.values()).allMatch(count -> count == 9L);
            assertThat(counts.containsKey(ClothesCategory.SKIRT)).isEqualTo(gender == Gender.FEMALE);
            assertThat(options.stream().map(option -> option.clothesId()).toList()).doesNotHaveDuplicates();
        }
    }

    @Test
    void filtersOppositeGenderAndInvalidVectorsAndPresignsImages() {
        profile(Gender.MALE);
        Clothes male = clothes(ClothesCategory.TOP, ClothesGender.MALE, 1);
        Clothes female = clothes(ClothesCategory.TOP, ClothesGender.FEMALE, 1);
        Clothes invalid = clothes(ClothesCategory.TOP, ClothesGender.BOTH, 0);
        when(clothesRepository.findSurveyCandidates()).thenReturn(List.of(male, female, invalid));
        when(storage.getPresignedUrl("image")).thenReturn("signed");
        assertThat(service.getOptions(userId)).singleElement().satisfies(option -> {
            assertThat(option.clothesId()).isEqualTo(male.getId());
            assertThat(option.imageUrl()).isEqualTo("signed");
        });
    }

    @Test
    void initializesFromArithmeticMean() {
        profile(Gender.FEMALE);
        Clothes first = clothes(ClothesCategory.TOP, ClothesGender.BOTH, 2);
        Clothes second = clothes(ClothesCategory.SKIRT, ClothesGender.FEMALE, 6);
        when(clothesRepository.findSurveyCandidates()).thenReturn(List.of(first, second));
        service.initialize(userId, List.of(first.getId(), second.getId()));
        var captor = ArgumentCaptor.forClass(float[].class);
        verify(vectors).initialize(eq(userId), captor.capture());
        assertThat(captor.getValue()).hasSize(1536);
        assertThat(captor.getValue()[0]).isEqualTo(4);
        assertThat(captor.getValue()[1]).isZero();
    }

    @Test
    void rejectsDuplicateEmptyAndNonSurveySelections() {
        profile(Gender.MALE);
        UUID id = UUID.randomUUID();
        when(clothesRepository.findSurveyCandidates()).thenReturn(List.of());
        assertThatThrownBy(() -> service.initialize(userId, List.of(id, id))).isInstanceOf(ClothesException.class);
        assertThatThrownBy(() -> service.initialize(userId, List.of())).isInstanceOf(ClothesException.class);
        assertThatThrownBy(() -> service.initialize(userId, List.of(id))).isInstanceOf(ClothesException.class);
        verifyNoInteractions(vectors);
    }

    @Test
    void acceptsSystemSelectionOutsideThePreviouslyFixedFirstNine() {
        profile(Gender.MALE);
        List<Clothes> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(clothes(ClothesCategory.TOP, ClothesGender.BOTH, i + 1));
        }
        candidates.sort(Comparator.comparing(Clothes::getId));
        when(clothesRepository.findSurveyCandidates()).thenReturn(candidates);
        Clothes selected = candidates.get(19);
        service.initialize(userId, List.of(selected.getId()));
        var captor = ArgumentCaptor.forClass(float[].class);
        verify(vectors).initialize(eq(userId), captor.capture());
        assertThat(captor.getValue()).containsExactly(selected.getAttributeVector());
    }

    private void profile(Gender gender) {
        when(profileRepository.findByUser_Id(userId)).thenReturn(Optional.of(Profile.builder().gender(gender).build()));
    }

    private Clothes clothes(ClothesCategory category, ClothesGender gender, float first) {
        float[] vector = new float[1536];
        vector[0] = first;
        return Clothes.builder().id(UUID.randomUUID()).category(category).gender(gender)
            .attributeVector(vector).imageUrl("image").build();
    }
}
