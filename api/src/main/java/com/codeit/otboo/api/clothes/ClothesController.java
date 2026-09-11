package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.ClothesAttributeResponse;
import com.codeit.otboo.api.clothes.dto.ClothesRequest;
import com.codeit.otboo.api.clothes.dto.ClothesResponse;
import com.codeit.otboo.api.clothes.dto.ClothesSearchRequest;
import com.codeit.otboo.api.clothes.dto.ClothesUpdateRequest;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clothes")
public class ClothesController {
    private final ClothesFacade clothesFacade;
    private final ClothesService clothesService;
    private final ClothesAnalysisService clothesAnalysisService;

    @GetMapping("/weblink-extractions")
    public ResponseEntity<ClothesResponse> getWeblinkClothesData(
        @RequestParam String url
    ) {
        ClothesAnalysisResult result = clothesAnalysisService.analyze(url);
        return ResponseEntity.ok(ClothesResponse.of(result));
    }

    @GetMapping("/image-extractions")
    public ResponseEntity<ClothesResponse> getImageClothesData() {
        return null;
    }

    @GetMapping("/attribute-defs")
    public ResponseEntity<List<ClothesAttributeResponse>> getClothesAttributes(
        @RequestParam(required = false) String sortBy, @RequestParam(required = false) String sortDirection, @RequestParam(required = false) String keywordLike
    ) {
        return ResponseEntity.ok(clothesService.getAttributes());
    }

    @PostMapping
    public ResponseEntity<ClothesResponse> post(
        @RequestPart("post") @Valid ClothesRequest req,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(clothesFacade.create(req, image));
    }

    @GetMapping
    public ResponseEntity<CursorResponse<ClothesResponse>> getClothes(
        @ModelAttribute @Valid ClothesSearchRequest req
    ) {
        return ResponseEntity.ok(clothesService.findAll(req));
    }

    @PatchMapping("/{clothesId}")
    public ResponseEntity<ClothesResponse> patchClothes(
        @PathVariable UUID clothesId,
        @RequestPart("request") @Valid ClothesUpdateRequest req,
        @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(clothesFacade.update(clothesId, req, image));
    }

    @DeleteMapping("/{clothesId}")
    public ResponseEntity<Void> deleteSoft(@PathVariable UUID clothesId) {
        clothesService.softDelete(clothesId);
        return ResponseEntity.noContent().build();
    }

}
