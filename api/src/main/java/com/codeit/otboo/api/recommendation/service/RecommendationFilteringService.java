package com.codeit.otboo.api.recommendation.service;

import com.codeit.otboo.api.recommendation.RecommendationType;
import com.codeit.otboo.api.recommendation.candidate.ClothesCandidateProvider;
import com.codeit.otboo.api.recommendation.filter.ClothesFilteringContext;
import com.codeit.otboo.api.recommendation.filter.RuleBasedClothesFilter;
import com.codeit.otboo.api.recommendation.temperature.TemperatureAdjustmentPolicy;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.exception.WeatherException;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
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
    private final ProfileRepository profileRepository;
    private final WeatherForecastRepository weatherForecastRepository;
    private final TemperatureAdjustmentPolicy temperatureAdjustmentPolicy;
    private final RuleBasedClothesFilter ruleBasedClothesFilter;

    public List<Clothes> filterOotd(UUID userId, UUID weatherId, Profile profile) {
        return filter(RecommendationType.OOTD, userId, weatherId, profile);
    }

    public List<Clothes> filterOutfit(UUID userId, UUID weatherId, Profile profile) {
        return filter(RecommendationType.OUTFIT, userId, weatherId, profile);
    }

    private List<Clothes> filter(
        RecommendationType type,
        UUID userId,
        UUID weatherId
    ) {
        Profile profile = profileRepository.findByUser_Id(userId)
            .orElseThrow(ProfileException::profileNotFound);
        return filter(type, userId, weatherId, profile);
    }

    private List<Clothes> filter(
        RecommendationType type,
        UUID userId,
        UUID weatherId,
        Profile profile
    ) {
        List<Clothes> candidates = providersByType().get(type).findCandidates(userId);

        WeatherForecast weather = weatherForecastRepository.findById(weatherId)
            .orElseThrow(() -> new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE));

        BigDecimal currentTemperature = weather.getTemperature();
        if (currentTemperature == null) {
            throw new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE);
        }

        BigDecimal effectiveTemperature = temperatureAdjustmentPolicy.calculateEffectiveTemperature(
            currentTemperature, profile.getTemperatureSensitivity());

        log.info(
            "[recommendation][filter] 필터링 시작 전. type={}, userId={}, weatherId={}, candidateCount={}, "
                + "candidateCountByCategory={}, currentTemperature={}, effectiveTemperature={}, gender={}",
            type,
            userId,
            weatherId,
            candidates.size(),
            countByCategory(candidates),
            currentTemperature,
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
