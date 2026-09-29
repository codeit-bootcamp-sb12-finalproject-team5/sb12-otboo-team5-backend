package com.codeit.otboo.api.recommendation.preference;

import com.codeit.otboo.api.recommendation.dto.UserPreferenceSurveyOption;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.profile.entity.Gender;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.VECTOR_DIMENSION;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceSurveyService {
    private final ClothesRepository clothesRepository;
    private final ProfileRepository profileRepository;
    private final S3StorageService s3StorageService;
    private final PreferenceVectorAsyncService preferenceVectorService;

    public List<UserPreferenceSurveyOption> getOptions(UUID requestingUserId) {
        Gender gender = findGender(requestingUserId);
        List<Clothes> candidates = new ArrayList<>(findSystemSurveyClothes(gender));
        Collections.shuffle(candidates);
        return surveyCategories(gender).stream()
            .flatMap(category -> candidates.stream()
                .filter(clothes -> clothes.getCategory() == category).limit(9))
            .map(clothes -> new UserPreferenceSurveyOption(clothes.getCategory(), clothes.getId(),
                s3StorageService.getPresignedUrl(clothes.getImageUrl())))
            .toList();
    }

    @Transactional
    public void initialize(UUID userId, List<UUID> clothesIds) {
        if (clothesIds == null || clothesIds.isEmpty() || clothesIds.size() > 45
            || clothesIds.stream().anyMatch(Objects::isNull)) {
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Set<UUID> ids = new HashSet<>(clothesIds);
        if (ids.size() != clothesIds.size()) {
            throw new ClothesException(ErrorCode.DUPLICATED_SELECTED_CLOTHES);
        }
        // 제출 시 다시 추첨하지 않고 시스템 의상 전체에서 선택 ID를 검증한다.
        List<Clothes> selected = findSystemSurveyClothes(findGender(userId)).stream()
            .filter(clothes -> ids.contains(clothes.getId())).toList();
        if (selected.size() != ids.size()) {
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE);
        }

        double[] sum = new double[VECTOR_DIMENSION];
        for (Clothes clothes : selected) {
            float[] vector = clothes.getAttributeVector();
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                sum[i] += vector[i];
            }
        }
        float[] mean = new float[VECTOR_DIMENSION];
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            mean[i] = (float) (sum[i] / selected.size());
        }
        if (!isUsableVector(mean)) {
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE);
        }
        preferenceVectorService.initialize(userId, mean);
    }

    private Gender findGender(UUID requestingUserId) {
        // 요청자 ID는 성별 조회에만 사용하며 의상 소유자 조건으로 사용하지 않는다.
        return profileRepository.findByUser_Id(requestingUserId)
            .orElseThrow(ProfileException::profileNotFound).getGender();
    }

    private List<ClothesCategory> surveyCategories(Gender gender) {
        return gender == Gender.MALE
            ? List.of(ClothesCategory.TOP, ClothesCategory.PANTS, ClothesCategory.OUTER, ClothesCategory.SHOES)
            : List.of(ClothesCategory.TOP, ClothesCategory.PANTS, ClothesCategory.SKIRT,
                ClothesCategory.OUTER, ClothesCategory.SHOES);
    }

    private List<Clothes> findSystemSurveyClothes(Gender gender) {
        List<ClothesCategory> categories = surveyCategories(gender);
        // Repository에서 삭제되지 않은 ADMIN 소유 의상만 조회한다.
        return clothesRepository.findSurveyCandidates().stream()
            .filter(clothes -> categories.contains(clothes.getCategory()))
            .filter(clothes -> matchesGender(gender, clothes.getGender()))
            .filter(clothes -> isUsableVector(clothes.getAttributeVector()))
            .toList();
    }

    private boolean matchesGender(Gender gender, ClothesGender clothesGender) {
        return gender == null || gender == Gender.OTHER || clothesGender == ClothesGender.BOTH
            || (gender == Gender.MALE && clothesGender == ClothesGender.MALE)
            || (gender == Gender.FEMALE && clothesGender == ClothesGender.FEMALE);
    }

    private boolean isUsableVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            return false;
        }
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
            norm += (double) value * value;
        }
        return norm > 0;
    }
}
