package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesAttributeResponse;
import com.codeit.otboo.api.clothes.dto.ClothesRequest;
import com.codeit.otboo.api.clothes.dto.ClothesResponse;
import com.codeit.otboo.api.clothes.dto.ClothesSearchRequest;
import com.codeit.otboo.api.clothes.dto.ClothesUpdateRequest;
import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import com.codeit.otboo.domain.clothes.exception.ClothesException;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import com.codeit.otboo.support.storage.FileStorage;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClothesService {
    private final ClothesRepository clothesRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;

    public List<ClothesAttributeResponse> getAttributes() {
        return List.of(
            ClothesAttributeResponse.of(ClothesSubCategory.class),  // 2-소분류
            ClothesAttributeResponse.of(ClothesColor.class),        // 3-색상
            ClothesAttributeResponse.of(ClothesFit.class),          // 4-핏
            ClothesAttributeResponse.of(ClothesMaterial.class),     // 5-소재
            ClothesAttributeResponse.of(ClothesPattern.class),      // 6-패턴
            ClothesAttributeResponse.of(ClothesStyle.class),        // 7-스타일
            ClothesAttributeResponse.of(ClothesSeason.class),       // 8-계절감
            ClothesAttributeResponse.of(ClothesGender.class)        // 9-의상성별
        );
    }

    @Transactional
    public ClothesResponse create(ClothesRequest req, String imageUrl) {
        User user = getCurrentUserEntity(getCurrentUserId());
        if (!req.ownerId().equals(user.getId())) {
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE).addDetail("사용자 Id값 입력이 유효하지 않습니다", null);
        }

        ClothesCategory category = req.type();
        ClothesSubCategory subCategory = req.getSubCategory();
        if (subCategory != null && category != subCategory.getCategory()) {
            log.info("Category : {} / SubCategory : {}", category, subCategory);
            throw new ClothesException(ErrorCode.INVALID_INPUT_VALUE).addDetail("의상 대분류에 유요한 소분류값이 아닙닏다", null);
        }

        Clothes clothes = Clothes.builder()
            .name(req.name())
            .isOwned(req.isOwned())
            .preference(req.preference())
            .imageUrl(imageUrl)
            .category(category)
            .gender(req.getGender())
            .attributeText(req.toEmbeddingText())
            .attributeVector(null)
            .user(user)
            .build();
        return ClothesResponse.of(clothesRepository.save(clothes), user.getId());
    }

    @Transactional(readOnly = true)
    public CursorResponse<ClothesResponse> findAll(ClothesSearchRequest req) {
        CursorResponse<Clothes> res = clothesRepository.findAllByDynamicQuery(
            req.cursor(), req.idAfter(), req.limit(), req.typeEqual(), req.ownerId()
        );
        List<ClothesResponse> data = res.data().stream()
            .map(e -> ClothesResponse.of(e, getCurrentUserId()))
            .toList();
        return CursorResponse.of(data, res.nextCursor(), res.nextIdAfter(), res.hasNext(), res.totalCount(), res.sortBy(), res.sortDirection());
    }

    @Transactional(readOnly = true)
    public Clothes find(UUID clothesId) {
        return clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public ClothesResponse update(UUID clothesId, ClothesUpdateRequest req, MultipartFile image) {
        Clothes clothes = clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!clothes.getUser().getId().equals(getCurrentUserId())) {
            throw new ClothesException(ErrorCode.ACCESS_DENIED).addDetail("수정대상은 사용자의 의상이 아닙니다.", null);
        }
        if (clothes.isDeleted()) {
            throw new ClothesException(ErrorCode.CLOTHES_NOT_FOUND).addDetail("이미 삭제된 의상입니다.", null);
        }

        clothes.setName(req.name());
        clothes.setCategory(req.type());
        clothes.setIsOwned(req.isOwned());
        if (req.preference() != null) clothes.setPreference(req.preference());
        clothes.setGender(req.getGender());
        clothes.setAttributeText(req.toEmbeddingText());
        if (image != null) {
            fileStorage.deleteOne(clothes.getImageUrl());
            clothes.setImageUrl(fileStorage.saveOne(image));
        }

        clothesRepository.save(clothes);
        return ClothesResponse.of(clothes, clothes.getUser().getId());
    }

    @Transactional
    public void softDelete(UUID clothesId) {
        Clothes clothes = clothesRepository.findById(clothesId)
            .orElseThrow(() -> new ClothesException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!clothes.getUser().getId().equals(getCurrentUserId())) {
            throw new ClothesException(ErrorCode.ACCESS_DENIED).addDetail("삭제대상은 사용자의 의상이 아닙니다.", null);
        }
        clothes.markDeleted();
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

}
