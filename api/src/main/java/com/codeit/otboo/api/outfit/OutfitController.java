package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.outfit.dto.*;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.support.llm.outfit.OutfitImageGenerateService;
import com.codeit.otboo.support.llm.outfit.dto.GeneratedOutfitImage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/outfit")
public class OutfitController {

    private final OutfitService outfitService;
    private final OutfitImageGenerateService outfitImageGenerateService;

    @PostMapping
    public ResponseEntity<OutfitCreateResponse> create(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody OutfitCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            outfitService.create(userDetails.getUserId(), request)
        );
    }

    @GetMapping
    public ResponseEntity<CursorResponse<OutfitListResponse>> getAll(
        @RequestParam(required = false) UUID cursor,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(outfitService.getAll(userDetails.getUserId(), cursor));
    }

    @GetMapping("/{outfitId}")
    public ResponseEntity<OutfitDetailResponse> get(
        @PathVariable UUID outfitId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(outfitService.get(outfitId, userDetails.getUserId()));
    }

    @PatchMapping("/{outfitId}")
    public ResponseEntity<OutfitUpdateResponse> update(
        @PathVariable UUID outfitId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody OutfitUpdateRequest request
    ) {
        return ResponseEntity.ok(outfitService.update(outfitId, userDetails.getUserId(), request));
    }

    @DeleteMapping("/{outfitId}")
    public ResponseEntity<Void> delete(
        @PathVariable UUID outfitId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        outfitService.delete(outfitId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{outfitId}/generate")
    public ResponseEntity<OutfitImageResponse> generate(
        @PathVariable UUID outfitId,
        @RequestParam OutfitImageGenerationType type,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        var outfit = outfitService.getOwnedOutfit(outfitId, userDetails.getUserId());
        GeneratedOutfitImage img = switch (type) {
            case FITTING -> outfitImageGenerateService.generateFitting(outfit);
            case COMPOSITION -> outfitImageGenerateService.generateOverview(outfit);
        };
        return ResponseEntity.ok(outfitService.createImage(img, outfitId, userDetails.getUserId()));
    }

    @PostMapping("/{outfitId}/image")
    public ResponseEntity<OutfitImageResponse> postImage(
        @PathVariable UUID outfitId,
        @RequestPart(value = "image") MultipartFile image,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(outfitService.createImage(image, outfitId, userDetails.getUserId()));
    }

    @DeleteMapping("/{outfitId}/image")
    public ResponseEntity<Void> deleteImage(
        @PathVariable UUID outfitId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        outfitService.deleteImage(outfitId, userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }

}
