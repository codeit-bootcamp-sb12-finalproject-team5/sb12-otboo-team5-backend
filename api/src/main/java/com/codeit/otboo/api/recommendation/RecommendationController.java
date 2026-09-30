package com.codeit.otboo.api.recommendation;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.recommendation.dto.*;
import com.codeit.otboo.api.recommendation.history.RecommendationDailyLimitPolicy;
import com.codeit.otboo.api.recommendation.service.RecommendationService;
import com.codeit.otboo.domain.recommendation.RecommendationType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;
    private final RecommendationDailyLimitPolicy recommendationDailyLimitPolicy;

    @GetMapping("/usage")
    public ResponseEntity<RecommendationUsageResponse> usage(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(RecommendationUsageResponse.of(
            recommendationDailyLimitPolicy.usage(principal.getUserId(), RecommendationType.OOTD),
            recommendationDailyLimitPolicy.usage(principal.getUserId(), RecommendationType.OUTFIT)
        ));
    }

    @GetMapping("/ootd")
    public ResponseEntity<RecommendationResponse> ootd(
        @AuthenticationPrincipal CustomUserDetails principal,
        @Valid @ModelAttribute RecommendationRequest request
    ) {
        return ResponseEntity.ok(recommendationService.recommendOotd(principal.getUserId(), request));
    }

    @GetMapping("/outfits")
    public ResponseEntity<RecommendationResponse> outfits(
        @AuthenticationPrincipal CustomUserDetails principal,
        @Valid @ModelAttribute RecommendationRequest request
    ) {
        return ResponseEntity.ok(recommendationService.recommendOutfit(principal.getUserId(), request));
    }

    @GetMapping("/preferences")
    public ResponseEntity<List<UserPreferenceSurveyOption>> getUserPreferenceOption(
        @AuthenticationPrincipal CustomUserDetails principal
    ) {
        return ResponseEntity.ok(recommendationService.getUserPreferenceOptions(principal.getUserId()));
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
