package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesAttributeResponse;
import com.codeit.otboo.api.clothes.dto.ClothesDataResponse;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes")
public class ClothesController {
    private final ClothesService clothesService;
    private final ClothesAnalysisService clothesAnalysisService;

    @GetMapping("/weblink-extractions")
    public ResponseEntity<ClothesDataResponse> getWeblinkClothesData(
        @RequestParam String url
    ) {
        ClothesAnalysisResult result = clothesAnalysisService.analyze(url);
        return ResponseEntity.ok(ClothesDataResponse.of(result));
    }

    @GetMapping("/image-extractions")
    public ResponseEntity<ClothesDataResponse> getImageClothesData() {
        return null;
    }

    @GetMapping("/attribute-defs")
    public ResponseEntity<List<ClothesAttributeResponse>> getClothesAttributes(
        @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortDirection, @RequestParam(required = false) String keywordLike
    ) {
        return ResponseEntity.ok(clothesService.getClothesAttributes());
    }

    @PostMapping
    public ResponseEntity<Object> postClothes() {
        return null;
    }

}
