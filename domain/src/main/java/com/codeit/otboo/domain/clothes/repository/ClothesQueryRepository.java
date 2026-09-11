package com.codeit.otboo.domain.clothes.repository;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import java.util.UUID;

public interface ClothesQueryRepository {

    CursorResponse<Clothes> findAllByDynamicQuery(
        String cursor,
        UUID idAfter,
        Integer limit,
        ClothesCategory typeEqual,
        UUID ownerId
    );

}
