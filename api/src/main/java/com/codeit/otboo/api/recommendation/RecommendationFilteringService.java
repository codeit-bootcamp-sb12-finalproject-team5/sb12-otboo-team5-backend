package com.codeit.otboo.api.recommendation;

import com.codeit.otboo.api.recommendation.candidate.ClothesCandidateProvider;
import com.codeit.otboo.api.recommendation.filter.ClothesFilteringContext;
import com.codeit.otboo.api.recommendation.filter.RuleBasedClothesFilter;
import com.codeit.otboo.api.recommendation.temperature.TemperatureAdjustmentPolicy;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.weather.entity.WeatherForecast;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationFilteringService {

    private final List<ClothesCandidateProvider> candidateProviders;
    private final ProfileRepository profileRepository;
    private final WeatherForecastRepository weatherForecastRepository;
    private final TemperatureAdjustmentPolicy temperatureAdjustmentPolicy;
    private final RuleBasedClothesFilter ruleBasedClothesFilter;

    public List<Clothes> filterOotd(UUID userId, UUID weatherId) {
        return filter(RecommendationType.OOTD, userId, weatherId);
    }

    public List<Clothes> filterOutfit(UUID userId, UUID weatherId) {
        return filter(RecommendationType.OUTFIT, userId, weatherId);
    }

    private List<Clothes> filter(RecommendationType type, UUID userId, UUID weatherId) {
        List<Clothes> candidates = providersByType().get(type).findCandidates(userId);

        Profile profile = profileRepository.findByUser_Id(userId)
            .orElseThrow(ProfileException::profileNotFound);
        WeatherForecast weather = weatherForecastRepository.findById(weatherId)
            .orElseThrow(() -> new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE));

        BigDecimal currentTemperature = weather.getTemperature();
        if (currentTemperature == null) {
            throw new WeatherException(ErrorCode.WEATHER_DATA_UNAVAILABLE);
        }

        BigDecimal effectiveTemperature = temperatureAdjustmentPolicy.calculateEffectiveTemperature(
            currentTemperature, profile.getTemperatureSensitivity());

        return ruleBasedClothesFilter.filter(candidates, new ClothesFilteringContext(effectiveTemperature));
    }

    private Map<RecommendationType, ClothesCandidateProvider> providersByType() {
        return candidateProviders.stream().collect(Collectors.toMap(
            ClothesCandidateProvider::getType, Function.identity()));
    }
}
