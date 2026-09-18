package com.codeit.otboo.api.recommendation;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.recommendation.dto.RecommendationResponse;
import com.codeit.otboo.api.recommendation.dto.UserPreferenceRequest;
import com.codeit.otboo.api.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    @GetMapping("/ootd")
    public ResponseEntity<RecommendationResponse> ootd(@AuthenticationPrincipal CustomUserDetails principal, @RequestParam UUID weatherId) {
        return ResponseEntity.ok(recommendationService.recommendOotd(principal.getUserId(), weatherId));
    }

    @GetMapping("/outfits")
    public ResponseEntity<RecommendationResponse> outfits(@AuthenticationPrincipal CustomUserDetails principal, @RequestParam UUID weatherId) {
        return ResponseEntity.ok(recommendationService.recommendOutfit(principal.getUserId(), weatherId));
    }

    @PostMapping("/preferences")
    public ResponseEntity<Void> setUserPreferences(
            @Valid @RequestBody UserPreferenceRequest userPreferenceRequest,
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        recommendationService.initializePreferenceVector(
                principal.getUserId(), userPreferenceRequest);
        return ResponseEntity.noContent().build();
    }
}
