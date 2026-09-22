package com.codeit.otboo.api.recommendation.service;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.api.recommendation.candidate.ClothesCandidateProvider;
import com.codeit.otboo.api.recommendation.filter.ClothesFilteringContext;
import com.codeit.otboo.api.recommendation.filter.RuleBasedClothesFilter;
import com.codeit.otboo.api.recommendation.temperature.TemperatureAdjustmentPolicy;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.weather.dto.WeatherInfoResponse;
import com.codeit.otboo.domain.weather.exception.WeatherException;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationFilteringService {

    private final List<ClothesCandidateProvider> candidateProviders;
    private final WeatherRepository weatherRepository;
    private final TemperatureAdjustmentPolicy temperatureAdjustmentPolicy;
    private final RuleBasedClothesFilter ruleBasedClothesFilter;

    public List<Clothes> filterOotd(UUID userId, UUID weatherId, Profile profile) {
        return filter(RecommendationType.OOTD, userId, weatherId, profile, List.of());
    }

    // 고정 의상 있는 경우
    public List<Clothes> filterOotd(UUID userId, UUID weatherId, Profile profile, List<Clothes> selectedClothes) {
        return filter(RecommendationType.OOTD, userId, weatherId, profile, selectedClothes);
    }

    public List<Clothes> filterOutfit(UUID userId, UUID weatherId, Profile profile) {
        return filter(RecommendationType.OUTFIT, userId, weatherId, profile, List.of());
    }

    // 고정 의상 있는 경우
    public List<Clothes> filterOutfit(UUID userId, UUID weatherId, Profile profile, List<Clothes> selectedClothes) {
        return filter(RecommendationType.OUTFIT, userId, weatherId, profile, selectedClothes);
    }

    /**
     * 선택된 고정 의상을 후보 풀에서 제거한 뒤 추가 후보만 필터링한다.
     */
    private List<Clothes> filter(
        RecommendationType type,
        UUID userId,
        UUID weatherId,
        Profile profile,
        List<Clothes> selectedClothes
    ) {
        WeatherInfoResponse weather = weatherRepository.findById(weatherId)
            .orElseThrow(() -> new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE));

        BigDecimal averageTemperature = weather.temperatureMin()
            .add(weather.temperatureMax())
            .divide(BigDecimal.valueOf(2));

        BigDecimal effectiveTemperature = temperatureAdjustmentPolicy.calculateEffectiveTemperature(
            averageTemperature, profile.getTemperatureSensitivity());

        // 후보군 리스트에서 선택된 옷들 제외
        Set<UUID> selectedClothesIds = selectedClothes.stream().map(Clothes::getId)
            .collect(Collectors.toUnmodifiableSet());
        List<Clothes> candidates = providersByType().get(type).findCandidates(userId).stream()
            .filter(clothes -> !selectedClothesIds.contains(clothes.getId()))
            .toList();

        log.info(
            "[recommendation][filter] 필터링 시작 전. type={}, userId={}, weatherId={}, candidateCount={}, "
                + "candidateCountByCategory={}, averageTemperature={}, effectiveTemperature={}, gender={}",
            type,
            userId,
            weatherId,
            candidates.size(),
            countByCategory(candidates),
            averageTemperature,
            effectiveTemperature,
            profile.getGender()
        );

        List<Clothes> filtered = ruleBasedClothesFilter.filter(candidates, new ClothesFilteringContext(
            effectiveTemperature,
            type,
            profile.getGender()
        ));

        log.info(
            "[recommendation][filter] 필터링 완료 type={}, userId={}, filteredCount={}, "
                + "filteredCountByCategory={}",
            type,
            userId,
            filtered.size(),
            countByCategory(filtered)
        );

        return filtered;
    }

    private Map<RecommendationType, ClothesCandidateProvider> providersByType() {
        return candidateProviders.stream().collect(Collectors.toMap(
            ClothesCandidateProvider::getType, Function.identity()));
    }

    private Map<String, Long> countByCategory(List<Clothes> clothes) {
        return clothes.stream().collect(Collectors.groupingBy(
            item -> item.getCategory().name(),
            TreeMap::new,
            Collectors.counting()
        ));
    }
}
