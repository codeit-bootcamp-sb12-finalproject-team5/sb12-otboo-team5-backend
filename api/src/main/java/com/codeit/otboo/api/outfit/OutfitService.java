package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.outfit.dto.*;
import com.codeit.otboo.api.recommendation.preference.ClothesContributionSnapshot;
import com.codeit.otboo.api.recommendation.preference.PreferenceVectorAsyncService;
import com.codeit.otboo.api.recommendation.temperature.TemperatureAdjustmentPolicy;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.entity.OutfitClothes;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.outfit.entity.Ootd;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.exception.OutfitException;
import com.codeit.otboo.domain.outfit.repository.OotdRepository;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.domain.weather.dto.WeatherInfoResponse;
import com.codeit.otboo.domain.weather.repository.WeatherForecastRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutfitService {

    private final OutfitRepository outfitRepository;
    private final OutfitClothesRepository outfitClothesRepository;
    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final WeatherForecastRepository weatherForecastRepository;
    private final WeatherRepository weatherRepository;
    private final OotdRepository ootdRepository;
    private final TemperatureAdjustmentPolicy temperatureAdjustmentPolicy;
    private final S3StorageService s3StorageService;
    private final PreferenceVectorAsyncService preferenceVectorAsyncService;

    @Transactional
    public OutfitCreateResponse create(UUID userId, OutfitCreateRequest request) {
        validateNoDuplicateClothesIds(request.clothesIds());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new OutfitException(ErrorCode.USER_NOT_FOUND));
        List<Clothes> clothes = getClothesInRequestOrder(request.clothesIds());

        Outfit outfit = outfitRepository.save(new Outfit(user, request.name(), request.category(), request.description()));
        outfitClothesRepository.saveAll(clothes.stream()
            .map(clothesItem -> new OutfitClothes(outfit, clothesItem))
            .toList());

        if (request.category().equals("OOTD") && request.weatherId() != null) {
            WeatherInfoResponse weatherForecast = weatherRepository.findById(request.weatherId())
                    .orElseThrow(() -> new OutfitException(ErrorCode.OOTD_WEATHER_FORECAST_NOT_FOUND));
            ootdRepository.save(Ootd.builder()
                    .outfit(outfit)
                    .skyStatus(weatherForecast.skyStatus())
                    .precipitationType(weatherForecast.precipitationType())
                    .precipitationAmount(weatherForecast.precipitationAmount())
                    .precipitationProbability(weatherForecast.precipitationProbability())
                    .temperatureCurrent(weatherForecast.temperatureCurrent())
                    .temperatureComparedToDayBefore(weatherForecast.temperatureComparedToDayBefore())
                    .temperatureMin(weatherForecast.temperatureMin())
                    .temperatureMax(weatherForecast.temperatureMax())
                    .build());
        } else {
            throw new OutfitException(ErrorCode.OOTD_INVALID_INPUT_VALUE);
        }

        List<ClothesContributionSnapshot> clothesContributions = snapshotsOf(clothes);
        runAfterCommit(() -> preferenceVectorAsyncService.increaseOutfitUsageContributions(
            userId,
            clothesContributions
        ));

        return OutfitCreateResponse.of(outfit, clothes);
    }

    @Transactional(readOnly = true)
    public OutfitDetailResponse get(UUID outfitId, UUID userId) {
        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(outfitId)
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));

        if (!outfit.getUser().getId().equals(userId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

        List<Clothes> clothes = outfitClothesRepository.findAllByOutfit_Id(outfitId).stream()
            .map(OutfitClothes::getClothes)
            .toList();

        Ootd ootd = null;
        if (outfit.getCategory().equals("OOTD")) {
            ootd = ootdRepository.findById(outfit.getId())
                    .orElseThrow(() -> new OutfitException(ErrorCode.OOTD_NOT_FOUND));
        }
        return OutfitDetailResponse.of(outfit, clothes, ootd, s3StorageService::getPresignedUrl);
    }

    @Transactional
    public OutfitUpdateResponse update(UUID outfitId, UUID userId, OutfitUpdateRequest request) {
        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(outfitId)
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));

        if (!outfit.getUser().getId().equals(userId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

        List<Clothes> previousClothes = request.clothesIds() == null
            ? List.of()
            : outfitClothesRepository.findAllByOutfit_Id(outfitId).stream()
                .map(OutfitClothes::getClothes)
                .toList();

        outfit.update(request.name(), request.description(), request.category());
        outfitRepository.saveAndFlush(outfit);

        List<Clothes> clothes;
        if (request.clothesIds() != null) {
            validateNoDuplicateClothesIds(request.clothesIds());
            clothes = getClothesInRequestOrder(request.clothesIds());
            outfitClothesRepository.deleteAllByOutfitId(outfitId);
            outfitClothesRepository.saveAll(clothes.stream()
                .map(clothesItem -> new OutfitClothes(outfit, clothesItem))
                .toList());
        } else {
            clothes = outfitClothesRepository.findAllByOutfit_Id(outfitId).stream()
                .map(OutfitClothes::getClothes)
                .toList();
        }

        if (request.clothesIds() != null) {
            List<ClothesContributionSnapshot> addedClothes = snapshotsOf(clothesOnlyIn(clothes, previousClothes));
            List<ClothesContributionSnapshot> removedClothes = snapshotsOf(clothesOnlyIn(previousClothes, clothes));
            runAfterCommit(() -> preferenceVectorAsyncService.updateOutfitUsageContributions(
                userId,
                addedClothes,
                removedClothes
            ));
        }

        return OutfitUpdateResponse.of(outfit, clothes);
    }

    @Transactional
    public void delete(UUID outfitId, UUID userId) {
        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(outfitId)
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));

        if (!outfit.getUser().getId().equals(userId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

        List<ClothesContributionSnapshot> clothesContributions = snapshotsOf(
            outfitClothesRepository.findAllByOutfit_Id(outfitId).stream()
                .map(OutfitClothes::getClothes)
                .toList()
        );
        outfit.markDeleted();
        runAfterCommit(() -> preferenceVectorAsyncService.decreaseOutfitUsageContributions(
            userId,
            clothesContributions
        ));
    }

    @Transactional(readOnly = true)
    public CursorResponse<OutfitListResponse> getAll(UUID userId, UUID cursor) {
        int limit = 12;
        List<Outfit> outfits = outfitRepository.findAllByUserIdAndCursor(
            userId, cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = outfits.size() > limit;
        if (hasNext) {
            outfits = outfits.subList(0, limit);
        }

        Map<UUID, List<Clothes>> clothesByOutfitId = outfits.isEmpty()
            ? Map.of() : outfitClothesRepository.findAllByOutfit_IdIn(outfits.stream().map(Outfit::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(
                outfitClothes -> outfitClothes.getOutfit().getId(),
                Collectors.mapping(OutfitClothes::getClothes, Collectors.toList())
            ));

        List<OutfitListResponse> data = outfits.stream()
            .map(outfit -> {
                Ootd ootd = null;
                if (outfit.getCategory().equals("OOTD")) {
                    ootd = ootdRepository.findById(outfit.getId())
                            .orElseThrow(() -> new OutfitException(ErrorCode.OOTD_NOT_FOUND));
                }
                return OutfitListResponse.of(
                        outfit,
                        clothesByOutfitId.getOrDefault(outfit.getId(), List.of()),
                        ootd,
                        s3StorageService::getPresignedUrl
                );
            })
            .toList();

        UUID nextOutfitId = hasNext ? outfits.get(outfits.size() - 1).getId() : null;
        return CursorResponse.of(
            data,
            nextOutfitId == null ? null : nextOutfitId.toString(),
            nextOutfitId,
            hasNext,
            outfitRepository.countByUser_IdAndDeletedAtIsNull(userId),
            "id",
            "DESCENDING"
        );
    }

    private void validateNoDuplicateClothesIds(List<UUID> clothesIds) {
        if (new HashSet<>(clothesIds).size() != clothesIds.size()) {
            throw new OutfitException(ErrorCode.DUPLICATE_OUTFIT_CLOTHES);
        }
    }

    private List<Clothes> getClothesInRequestOrder(List<UUID> clothesIds) {
        List<Clothes> foundClothes = clothesRepository.findAllById(clothesIds);

        Map<UUID, Clothes> clothesById = new HashMap<>();
        for (Clothes clothes : foundClothes) {
            if (!clothes.isDeleted()) {
                clothesById.put(clothes.getId(), clothes);
            }
        }

        if (clothesById.size() != clothesIds.size()) {
            throw new ClothesException(ErrorCode.CLOTHES_NOT_FOUND);
        }
        return clothesIds.stream().map(clothesById::get).toList();
    }

    private List<Clothes> clothesOnlyIn(List<Clothes> source, List<Clothes> compared) {
        Set<UUID> comparedClothesIds = compared.stream().map(Clothes::getId).collect(Collectors.toSet());
        return source.stream()
            .filter(clothes -> !comparedClothesIds.contains(clothes.getId()))
            .toList();
    }

    private List<ClothesContributionSnapshot> snapshotsOf(List<Clothes> clothes) {
        return clothes.stream().map(ClothesContributionSnapshot::from).toList();
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
