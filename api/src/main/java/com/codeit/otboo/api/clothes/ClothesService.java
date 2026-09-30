package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.*;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.recommendation.preference.PreferenceVectorAsyncService;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.*;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesService {
    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;
    private final EmbeddingAsyncService embeddingAsyncService;
    private final PreferenceVectorAsyncService preferenceVectorAsyncService;

    public List<ClothesAttributeResponse> getAttributes() {
        return List.of(
            ClothesAttributeResponse.of(ClothesSubCategory.class),  // 2-소분류
            ClothesAttributeResponse.of(ClothesColor.class),        // 3-색상
            ClothesAttributeResponse.of(ClothesFit.class),          // 4-핏
            ClothesAttributeResponse.of(ClothesMaterial.class),     // 5-소재
            ClothesAttributeResponse.of(ClothesPattern.class),      // 6-패턴
            ClothesAttributeResponse.of(ClothesStyle.class)         // 7-스타일
        );
    }

    @Transactional
    public Clothes create(ClothesRequest req, MultipartFile image) {
        User user = getCurrentUserEntity(getCurrentUserId());
        if (!req.ownerId().equals(user.getId())) {
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE).addDetail("사용자 Id값 입력이 유효하지 않습니다", null);
        }
        Clothes clothes = save(req, image, user);
        UUID clothesId = clothes.getId();
        String attributeText = clothes.getAttributeText();
        runAfterCommit(() -> embeddingAsyncService.createClothesEmbedding(clothesId, attributeText));
        return clothes;
    }

    private Clothes save(ClothesRequest req, MultipartFile image, User user) {
        ClothesCategory category = req.type();
        ClothesSubCategory subCategory = req.getSubCategory();
        if (subCategory != null && category != subCategory.getParentCategory()) {
            log.info("Category : {} / SubCategory : {}", category, subCategory);
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE).addDetail("의상 대분류에 유요한 소분류값이 아닙닏다", null);
        }

        Clothes clothes = Clothes.builder()
            .name(req.name())
            .isOwned(req.isOwned())
            .preference(req.preference())
            .imageUrl(null)
            .category(category)
            .gender(req.gender())
            .brand(req.brand())
            .season(req.season())
            .attributeText(req.toEmbeddingText())
            .description(req.description())
            .attributeVector(null)
            .user(user)
            .build();
        clothes = clothesRepository.save(clothes);

        if (image != null) {
            String imageUrl = s3StorageService.saveClothes(image, clothes.getId());
            clothes.setImageUrl(imageUrl);
        }
        return clothes;
    }

    @Transactional(readOnly = true)
    public CursorResponse<ClothesResponse> findAll(ClothesSearchRequest req) {
        CursorResponse<Clothes> res = clothesRepository.findAllByDynamicQuery(
            req.cursor(), req.idAfter(), req.limit(), req.typeEqual(), req.ownerId()
        );
        List<ClothesResponse> data = res.data().stream()
            .map(e -> ClothesResponse.of(e, getCurrentUserId(), s3StorageService.getPresignedUrl(e.getImageUrl())))
            .toList();
        return CursorResponse.of(data, res.nextCursor(), res.nextIdAfter(), res.hasNext(), res.totalCount(), res.sortBy(), res.sortDirection());
    }

    @Transactional(readOnly = true)
    public Clothes find(UUID clothesId) {
        return clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public Clothes update(UUID clothesId, ClothesUpdateRequest req, MultipartFile image) {
        Clothes clothes = clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!clothes.getUser().getId().equals(getCurrentUserId())) {
            throw new ClothesException(ErrorCode.ACCESS_DENIED).addDetail("수정대상은 사용자의 의상이 아닙니다.", null);
        }
        if (clothes.isDeleted()) {
            throw new ClothesException(ErrorCode.CLOTHES_NOT_FOUND).addDetail("이미 삭제된 의상입니다.", null);
        }

        Integer previousPreference = clothes.getPreference();
        float[] previousAttributeVector = copyOf(clothes.getAttributeVector());

        clothes.update(
                req.name(),
                req.brand(),
                req.type(),
                req.season(),
                req.gender(),
                req.toEmbeddingText(),
                req.description(),
                req.isOwned(),
                req.preference()
        );

        if (image != null) {
            s3StorageService.deleteOne(clothes.getImageUrl());
            clothes.setImageUrl(s3StorageService.saveClothes(image, clothes.getId()));
        }

        Clothes updatedClothes = clothesRepository.save(clothes);
        UUID userId = updatedClothes.getUser().getId();
        String attributeText = updatedClothes.getAttributeText();
        Integer updatedPreference = updatedClothes.getPreference();
        runAfterCommit(() -> embeddingAsyncService.updateClothesEmbedding(clothesId, attributeText));
        if (!Objects.equals(previousPreference, updatedPreference)) {
            runAfterCommit(() -> preferenceVectorAsyncService.updateClothesPreferenceContribution(
                userId,
                clothesId,
                previousPreference,
                updatedPreference,
                previousAttributeVector
            ));
        }

        return updatedClothes;
    }

    @Transactional
    public void softDelete(UUID clothesId) {
        Clothes clothes = clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!clothes.getUser().getId().equals(getCurrentUserId())) {
            throw new ClothesException(ErrorCode.ACCESS_DENIED).addDetail("삭제대상은 사용자의 의상이 아닙니다.", null);
        }
        UUID userId = clothes.getUser().getId();
        Integer preference = clothes.getPreference();
        float[] attributeVector = copyOf(clothes.getAttributeVector());
        clothes.markDeleted();
        runAfterCommit(() -> preferenceVectorAsyncService.removeClothesContribution(
            userId,
            clothesId,
            preference,
            attributeVector
        ));
    }

    private UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("로그인된 사용자가 없습니다.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new IllegalStateException("알 수 없는 인증 정보입니다.");
    }
    private User getCurrentUserEntity(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalStateException("DB에서 사용자를 찾을 수 없습니다."));
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

    private float[] copyOf(float[] vector) {
        return vector == null ? null : vector.clone();
    }


}
