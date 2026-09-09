package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesAttributeResponse;
import com.codeit.otboo.domain.clothes.enums.ClothesCategory;
import com.codeit.otboo.domain.clothes.enums.ClothesColor;
import com.codeit.otboo.domain.clothes.enums.ClothesFit;
import com.codeit.otboo.domain.clothes.enums.ClothesGender;
import com.codeit.otboo.domain.clothes.enums.ClothesMaterial;
import com.codeit.otboo.domain.clothes.enums.ClothesPattern;
import com.codeit.otboo.domain.clothes.enums.ClothesSeason;
import com.codeit.otboo.domain.clothes.enums.ClothesStyle;
import com.codeit.otboo.domain.clothes.enums.ClothesSubCategory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClothesService {

    public List<ClothesAttributeResponse> getClothesAttributes() {
        return List.of(
            ClothesAttributeResponse.of(ClothesGender.class),
            ClothesAttributeResponse.of(ClothesCategory.class),
            ClothesAttributeResponse.of(ClothesSubCategory.class),
            ClothesAttributeResponse.of(ClothesColor.class),
            ClothesAttributeResponse.of(ClothesFit.class),
            ClothesAttributeResponse.of(ClothesMaterial.class),
            ClothesAttributeResponse.of(ClothesPattern.class),
            ClothesAttributeResponse.of(ClothesStyle.class),
            ClothesAttributeResponse.of(ClothesSeason.class)
        );
    }

}
