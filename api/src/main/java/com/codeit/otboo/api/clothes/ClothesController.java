package com.codeit.otboo.api.clothes;

import com.codeit.otboo.api.clothes.dto.*;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisResult;
import com.codeit.otboo.support.openai.clothes.ClothesAnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

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

    @PostMapping("/bulk")
    public ResponseEntity<Void> postBulk(
            @RequestBody BulkClothesRequest req
    ) {
        int total = req.links().size();
        List<CompletableFuture<BulkClothesProcessResult>> futures =
                IntStream.range(0, total)
                        .mapToObj(i ->
                                clothesFacade.process(
                                        req.userId(),
                                        req.links().get(i),
                                        i + 1,
                                        total
                                )
                        )
                        .toList();
        List<BulkClothesProcessResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        long successCount = results.stream()
                .filter(BulkClothesProcessResult::success)
                .count();
        log.info("벌크 의류 적재 완료: total={}, success={}, failure={}",
                total, successCount, total - successCount);
        return ResponseEntity.ok().build();
    }

}
