package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesRequest;
import com.codeit.otboo.api.clothes.dto.ClothesResponse;
import com.codeit.otboo.api.clothes.dto.ClothesUpdateRequest;
import com.codeit.otboo.support.storage.FileStorage;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ClothesFacade {
    private final ClothesService clothesService;
    private final FileStorage fileStorage;

    public ClothesResponse create(ClothesRequest req, MultipartFile image) {
        String imageUrl = null;
        if (image != null) {
            imageUrl = fileStorage.saveOne(image);
        }
        ClothesResponse res = clothesService.create(req, imageUrl);
        // -> 비동기 벡터임베딩 작업!
        return res;
    }

    public ClothesResponse update(UUID clothesId, ClothesUpdateRequest req, MultipartFile image) {
        ClothesResponse res = clothesService.update(clothesId, req, image);
        // -> 비동기 벡터임베딩 작업!
        return res;
    }

}
