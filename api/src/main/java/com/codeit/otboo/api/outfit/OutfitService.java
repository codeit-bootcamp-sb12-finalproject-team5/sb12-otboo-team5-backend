package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.outfit.dto.*;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.entity.OutfitClothes;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.exception.OutfitException;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OutfitService {

    private final OutfitRepository outfitRepository;
    private final OutfitClothesRepository outfitClothesRepository;
    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;

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
        return OutfitDetailResponse.of(outfit, clothes);
    }

    @Transactional
    public OutfitUpdateResponse update(UUID outfitId, UUID userId, OutfitUpdateRequest request) {
        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(outfitId)
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));

        if (!outfit.getUser().getId().equals(userId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

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

        return OutfitUpdateResponse.of(outfit, clothes);
    }

    @Transactional
    public void delete(UUID outfitId, UUID userId) {
        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(outfitId)
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));

        if (!outfit.getUser().getId().equals(userId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

        outfit.markDeleted();
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
            .map(outfit -> OutfitListResponse.of(
                outfit, clothesByOutfitId.getOrDefault(outfit.getId(), List.of())))
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
}
