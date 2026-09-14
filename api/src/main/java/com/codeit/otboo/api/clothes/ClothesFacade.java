package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesRequest;
import com.codeit.otboo.api.clothes.dto.ClothesResponse;
import com.codeit.otboo.api.clothes.dto.ClothesUpdateRequest;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.support.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClothesFacade {
    private final ClothesService clothesService;
    private final EmbeddingAsyncService embeddingAsyncService;
    private final FileStorage fileStorage;

    public ClothesResponse create(ClothesRequest req, MultipartFile image) {
        String imageUrl = null;
        if (image != null) {
            imageUrl = fileStorage.saveOne(image);
        }
        Clothes clothes = clothesService.create(req, imageUrl);
        embeddingAsyncService.clothesEmbedding(clothes.getId(), clothes.getAttributeText());
        return ClothesResponse.of(clothes, clothes.getUser().getId());
    }

    public ClothesResponse update(UUID clothesId, ClothesUpdateRequest req, MultipartFile image) {
        Clothes clothes = clothesService.update(clothesId, req, image);
        embeddingAsyncService.clothesEmbedding(clothes.getId(), clothes.getAttributeText());
        return ClothesResponse.of(clothes, clothes.getUser().getId());
    }

}
