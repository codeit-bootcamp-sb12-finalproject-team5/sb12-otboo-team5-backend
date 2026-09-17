package com.codeit.otboo.api.outfit;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.outfit.dto.OutfitCreateRequest;
import com.codeit.otboo.api.outfit.dto.OutfitCreateResponse;
import com.codeit.otboo.api.outfit.dto.OutfitUpdateRequest;
import com.codeit.otboo.api.outfit.dto.OutfitUpdateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/outfit")
public class OutfitController {

    private final OutfitService outfitService;

    @PostMapping
    public ResponseEntity<OutfitCreateResponse> create(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody OutfitCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            outfitService.create(userDetails.getUserId(), request)
        );
    }

    @PatchMapping("/{outfitId}")
    public ResponseEntity<OutfitUpdateResponse> update(
        @PathVariable UUID outfitId,
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody OutfitUpdateRequest request
    ) {
        return ResponseEntity.ok(outfitService.update(outfitId, userDetails.getUserId(), request));
    }
}
