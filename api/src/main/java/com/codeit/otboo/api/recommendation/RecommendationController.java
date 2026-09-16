package com.codeit.otboo.api.recommendation;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.recommendation.dto.UserPreferenceRequest;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recommendation")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping("/ootd")
    public ResponseEntity<List<Clothes>> getOotdRecommendations() {
        // Implementation for getting OOTD recommendations
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/outfit")
    public ResponseEntity<List<Clothes>> getOutfitRecommendations() {
        // Implementation for getting outfit recommendations
        return ResponseEntity.ok(List.of());
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
