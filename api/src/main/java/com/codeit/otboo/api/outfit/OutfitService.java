package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.outfit.dto.OutfitCreateRequest;
import com.codeit.otboo.api.outfit.dto.OutfitCreateResponse;
import com.codeit.otboo.api.outfit.dto.OutfitUpdateRequest;
import com.codeit.otboo.api.outfit.dto.OutfitUpdateResponse;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.entity.OutfitClothes;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.clothes.repository.OutfitClothesRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.outfit.entity.Outfit;
import com.codeit.otboo.domain.outfit.exception.OutfitException;
import com.codeit.otboo.domain.outfit.repository.OutfitRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Outfit outfit = outfitRepository.save(new Outfit(user, request.name(), request.description()));
        outfitClothesRepository.saveAll(clothes.stream()
            .map(clothesItem -> new OutfitClothes(outfit, clothesItem))
            .toList());

        return OutfitCreateResponse.of(outfit, clothes);
    }

    @Transactional
    public OutfitUpdateResponse update(UUID outfitId, UUID userId, OutfitUpdateRequest request) {
        Outfit outfit = outfitRepository.findByIdAndDeletedAtIsNull(outfitId)
            .orElseThrow(() -> new OutfitException(ErrorCode.OUTFIT_NOT_FOUND));

        if (!outfit.getUser().getId().equals(userId)) {
            throw new OutfitException(ErrorCode.ACCESS_DENIED);
        }

        outfit.update(request.name(), request.description());
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
