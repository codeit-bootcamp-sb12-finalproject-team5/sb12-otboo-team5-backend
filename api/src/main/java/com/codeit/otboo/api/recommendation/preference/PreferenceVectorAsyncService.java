package com.codeit.otboo.api.recommendation.preference;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesUsageCount;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.codeit.otboo.api.recommendation.ranking.RecommendationRankingPolicy.VECTOR_DIMENSION;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceVectorAsyncService {

    private final ClothesAnalysisService clothesAnalysisService;
    private final ClothesRepository clothesRepository;
    private final OutfitClothesRepository outfitClothesRepository;
    private final ProfileRepository profileRepository;

    @Async("taskExecutor")
    @Transactional
    public void initialize(UUID userId, String surveyText) {
        log.info("[recommendation][preference] 선호 벡터 비동기 초기화를 시작 userId={}", userId);

        float[] surveyVector = clothesAnalysisService.embed(surveyText);
        List<Clothes> clothes = clothesRepository.findAllByUser_IdAndDeletedAtIsNull(userId);
        Map<UUID, Long> outfitUsageCountByClothesId = outfitClothesRepository
            .countActiveOutfitUsageByUserId(userId)
            .stream()
            .collect(Collectors.toMap(
                OutfitClothesUsageCount::getClothesId,
                OutfitClothesUsageCount::getUsageCount
            ));

        double[] preferenceVectorSum = initializeSurveyContribution(surveyVector);
        int contributedClothesCount = addClothesContributions(
            preferenceVectorSum,
            clothes,
            outfitUsageCountByClothesId
        );

        Profile profile = profileRepository.findByUser_Id(userId)
            .orElseThrow(ProfileException::profileNotFound);
        profile.updatePreferenceVector(toFloatArray(preferenceVectorSum));

        log.info(
            "[recommendation][preference] 선호 벡터 비동기 초기화가 완료 userId={}, clothesCount={}, "
                + "contributedClothesCount={}, vectorDimension={}",
            userId,
            clothes.size(),
            contributedClothesCount,
            VECTOR_DIMENSION
        );
    }

    @Async("taskExecutor")
    @Transactional
    public void addClothesContribution(
        UUID userId,
        UUID clothesId,
        Integer preference,
        float[] attributeVector
    ) {
        applyClothesContribution(
            userId,
            clothesId,
            attributeVector,
            clothesWeight(userId, clothesId, preference),
            "추가"
        );
    }

    @Async("taskExecutor")
    @Transactional
    public void removeClothesContribution(
        UUID userId,
        UUID clothesId,
        Integer preference,
        float[] attributeVector
    ) {
        applyClothesContribution(
            userId,
            clothesId,
            attributeVector,
            -clothesWeight(userId, clothesId, preference),
            "삭제"
        );
    }

    @Async("taskExecutor")
    @Transactional
    public void updateClothesPreferenceContribution(
        UUID userId,
        UUID clothesId,
        Integer previousPreference,
        Integer updatedPreference,
        float[] attributeVector
    ) {
        long outfitUsageCount = outfitClothesRepository
            .countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId);
        double contributionWeight = PreferenceVectorPolicy.clothesWeight(updatedPreference, outfitUsageCount)
            - PreferenceVectorPolicy.clothesWeight(previousPreference, outfitUsageCount);

        applyClothesContribution(userId, clothesId, attributeVector, contributionWeight, "선호도 변경");
    }

    @Async("taskExecutor")
    @Transactional
    public void increaseOutfitUsageContributions(
        UUID userId,
        List<ClothesContributionSnapshot> clothes
    ) {
        applyOutfitUsageContributions(userId, clothes, 1, "아웃핏 생성 또는 의상 추가");
    }

    @Async("taskExecutor")
    @Transactional
    public void decreaseOutfitUsageContributions(
        UUID userId,
        List<ClothesContributionSnapshot> clothes
    ) {
        applyOutfitUsageContributions(userId, clothes, -1, "아웃핏 삭제 또는 의상 제거");
    }

    @Async("taskExecutor")
    @Transactional
    public void updateOutfitUsageContributions(
        UUID userId,
        List<ClothesContributionSnapshot> addedClothes,
        List<ClothesContributionSnapshot> removedClothes
    ) {
        applyOutfitUsageContributions(userId, addedClothes, 1, "아웃핏 수정 의상 추가");
        applyOutfitUsageContributions(userId, removedClothes, -1, "아웃핏 수정 의상 제거");
    }

    private double[] initializeSurveyContribution(float[] surveyVector) {
        double[] sum = new double[VECTOR_DIMENSION];
        for (int index = 0; index < VECTOR_DIMENSION; index++) {
            sum[index] = PreferenceVectorPolicy.SURVEY_VECTOR_WEIGHT * surveyVector[index];
        }
        return sum;
    }
    private int addClothesContributions(double[] preferenceVectorSum, List<Clothes> clothes, Map<UUID, Long> outfitUsageCountByClothesId) {
        int contributedClothesCount = 0;
        for (Clothes clothesItem : clothes) {
            float[] attributeVector = clothesItem.getAttributeVector();
            if (!isUsableVector(attributeVector)) {
                log.warn(
                    "[recommendation][preference] 의상 속성 벡터가 없어 기여도에서 제외 clothesId={}",
                    clothesItem.getId()
                );
                continue;
            }

            long outfitUsageCount = outfitUsageCountByClothesId.getOrDefault(clothesItem.getId(), 0L);
            double clothesWeight = PreferenceVectorPolicy.clothesWeight(
                clothesItem.getPreference(),
                outfitUsageCount
            );
            if (clothesWeight == 0.0) {
                continue;
            }

            for (int index = 0; index < VECTOR_DIMENSION; index++) {
                preferenceVectorSum[index] += clothesWeight * attributeVector[index];
            }
            contributedClothesCount++;
        }
        return contributedClothesCount;
    }

    private double clothesWeight(UUID userId, UUID clothesId, Integer preference) {
        long outfitUsageCount = outfitClothesRepository
            .countActiveOutfitUsageByUserIdAndClothesId(userId, clothesId);
        return PreferenceVectorPolicy.clothesWeight(preference, outfitUsageCount);
    }

    private void applyClothesContribution(
        UUID userId,
        UUID clothesId,
        float[] attributeVector,
        double contributionWeight,
        String operation
    ) {
        if (contributionWeight == 0.0) {
            return;
        }
        if (!isUsableVector(attributeVector)) {
            log.warn(
                "[recommendation][preference] 의상 속성 벡터가 없어 기여도 갱신을 건너뜀 operation={}, clothesId={}",
                operation,
                clothesId
            );
            return;
        }

        Profile profile = profileRepository.findByUser_Id(userId)
            .orElseThrow(ProfileException::profileNotFound);
        float[] preferenceVector = profile.getPreferenceVector();
        if (!isUsableVector(preferenceVector)) {
            log.warn(
                "[recommendation][preference] 사용자 선호 벡터가 유효하지 않아 기여도 갱신을 건너뜀 operation={}, userId={}",
                operation,
                userId
            );
            return;
        }

        float[] updatedPreferenceVector = preferenceVector.clone();
        for (int index = 0; index < VECTOR_DIMENSION; index++) {
            updatedPreferenceVector[index] += (float) (contributionWeight * attributeVector[index]);
        }
        profile.updatePreferenceVector(updatedPreferenceVector);

        log.info(
            "[recommendation][preference] 의상 기여도 갱신 완료 operation={}, userId={}, clothesId={}, weight={}",
            operation,
            userId,
            clothesId,
            contributionWeight
        );
    }

    private void applyOutfitUsageContributions(
        UUID userId,
        List<ClothesContributionSnapshot> clothes,
        int usageCountChange,
        String operation
    ) {
        if (clothes.isEmpty()) {
            return;
        }

        Profile profile = profileRepository.findByUser_Id(userId)
            .orElseThrow(ProfileException::profileNotFound);
        float[] preferenceVector = profile.getPreferenceVector();
        if (!isUsableVector(preferenceVector)) {
            log.warn(
                "[recommendation][preference] 사용자 선호 벡터가 유효하지 않아 아웃핏 기여도 갱신을 건너뜀 operation={}, userId={}",
                operation,
                userId
            );
            return;
        }

        float[] updatedPreferenceVector = preferenceVector.clone();
        int adjustedClothesCount = 0;
        for (ClothesContributionSnapshot clothesItem : clothes) {
            float[] attributeVector = clothesItem.attributeVector();
            if (!isUsableVector(attributeVector)) {
                log.warn(
                    "[recommendation][preference] 의상 속성 벡터가 없어 아웃핏 기여도 갱신을 건너뜀 operation={}, clothesId={}",
                    operation,
                    clothesItem.clothesId()
                );
                continue;
            }

            long currentUsageCount = outfitClothesRepository
                .countActiveOutfitUsageByUserIdAndClothesId(userId, clothesItem.clothesId());
            long previousUsageCount = currentUsageCount - usageCountChange;
            if (previousUsageCount < 0) {
                log.warn(
                    "[recommendation][preference] 아웃핏 빈도 변화가 유효하지 않아 기여도 갱신을 건너뜀 operation={}, clothesId={}, currentUsageCount={}",
                    operation,
                    clothesItem.clothesId(),
                    currentUsageCount
                );
                continue;
            }

            double contributionWeight = PreferenceVectorPolicy.clothesWeight(
                clothesItem.preference(),
                currentUsageCount
            ) - PreferenceVectorPolicy.clothesWeight(
                clothesItem.preference(),
                previousUsageCount
            );
            if (contributionWeight == 0.0) {
                continue;
            }

            for (int index = 0; index < VECTOR_DIMENSION; index++) {
                updatedPreferenceVector[index] += (float) (contributionWeight * attributeVector[index]);
            }
            adjustedClothesCount++;
        }

        if (adjustedClothesCount == 0) {
            return;
        }
        profile.updatePreferenceVector(updatedPreferenceVector);
        log.info(
            "[recommendation][preference] 아웃핏 기여도 갱신 완료 operation={}, userId={}, clothesCount={}",
            operation,
            userId,
            adjustedClothesCount
        );
    }

    private boolean isUsableVector(float[] vector) {
        if (vector == null || vector.length != VECTOR_DIMENSION) {
            return false;
        }

        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
    private float[] toFloatArray(double[] vector) {
        float[] result = new float[VECTOR_DIMENSION];
        for (int index = 0; index < VECTOR_DIMENSION; index++) {
            result[index] = (float) vector[index];
        }
        return result;
    }
}
