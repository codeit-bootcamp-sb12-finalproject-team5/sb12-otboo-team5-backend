package com.codeit.otboo.api.recommendation.llm;

import com.codeit.otboo.domain.recommendation.OutfitFingerprintGenerator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class LlmRecommendationValidator {
    public ValidationResult validate(LlmRecommendationRequest request, LlmRecommendationResponse response) {
        List<LlmRecommendationResponse.GeneratedOutfit> outfits = response.outfits();
        if (outfits == null || outfits.isEmpty() || outfits.size() > 3)
            return ValidationResult.invalid(ValidationFailureReason.INVALID_RANK);

        Map<UUID, String> categories = new HashMap<>();
        Map<UUID, String> roles = new HashMap<>();
        Set<UUID> selectedClothesIds = new HashSet<>();
        for (LlmRecommendationRequest.LlmClothesCandidate selectedClothes : request.selectedClothes()) {
            categories.put(selectedClothes.id(), selectedClothes.category());
            roles.put(selectedClothes.id(), selectedClothes.role());
            selectedClothesIds.add(selectedClothes.id());
        }

        for (LlmRecommendationRequest.LlmClothesCandidate candidate : request.candidates()) {
            categories.put(candidate.id(), candidate.category());
            roles.put(candidate.id(), candidate.role());
        }

        Set<Set<UUID>> outfitSets = new HashSet<>();
        for (int index = 0; index < outfits.size(); index++) {
            LlmRecommendationResponse.GeneratedOutfit outfit = outfits.get(index);

            if (outfit.rank() != index + 1) return ValidationResult.invalid(ValidationFailureReason.INVALID_RANK);

            if (outfit.reason() == null || outfit.reason().isBlank())
                return ValidationResult.invalid(ValidationFailureReason.EMPTY_REASON);

            List<UUID> ids = outfit.clothesIds();
            if (ids == null || ids.size() != new HashSet<>(ids).size())
                return ValidationResult.invalid(ValidationFailureReason.DUPLICATED_CLOTHES);

            if (!categories.keySet().containsAll(ids))
                return ValidationResult.invalid(ValidationFailureReason.UNKNOWN_CLOTHES_ID);

            if (!ids.containsAll(selectedClothesIds))
                return ValidationResult.invalid(ValidationFailureReason.MISSING_SELECTED_CLOTHES);

            if (ids.stream().anyMatch(id -> !isAllowedCategory(categories.get(id))))
                return ValidationResult.invalid(ValidationFailureReason.INVALID_CATEGORY);

            if (!outfitSets.add(Set.copyOf(ids)))
                return ValidationResult.invalid(ValidationFailureReason.DUPLICATED_OUTFIT);

            long tops = count(roles, ids, "TOP");
            long bottoms = count(roles, ids, "BOTTOM");
            long dresses = count(roles, ids, "ONE_PIECE");
            boolean hasTwoPiece = tops >= 1 && bottoms == 1 && dresses == 0;
            boolean hasOnePiece = dresses == 1 && tops == 0 && bottoms == 0;
            if (!hasTwoPiece && !hasOnePiece) {
                return ValidationResult.invalid(ValidationFailureReason.INVALID_BASIC_OUTFIT);
            }

            if (count(categories, ids, "OUTER") > 1
                || count(categories, ids, "SHOES") > 1
                || count(categories, ids, "HAT") > 1
                || count(categories, ids, "BAG") > 1
                || count(categories, ids, "ACCESSORY") > 1) {
                return ValidationResult.invalid(ValidationFailureReason.INVALID_OPTIONAL_COUNT);
            }
        }

        return ValidationResult.valid();
    }

    public LlmRecommendationResponse removeRecentDuplicateOutfits(
        LlmRecommendationResponse response,
        Set<String> recentFingerprints
    ) {
        List<LlmRecommendationResponse.GeneratedOutfit> filtered = response.outfits().stream()
            .filter(outfit -> !recentFingerprints.contains(OutfitFingerprintGenerator.generate(outfit.clothesIds())))
            .toList();
        return new LlmRecommendationResponse(java.util.stream.IntStream.range(0, filtered.size())
            .mapToObj(index -> {
                LlmRecommendationResponse.GeneratedOutfit outfit = filtered.get(index);
                return new LlmRecommendationResponse.GeneratedOutfit(
                    index + 1, outfit.clothesIds(), outfit.reason(), outfit.styleTags());
            }).toList());
    }

    private long count(Map<UUID, String> values, List<UUID> ids, String value) {
        return ids.stream().filter(id -> value.equals(values.get(id))).count();
    }

    private boolean isAllowedCategory(String category) {
        return "TOP".equals(category) || "PANTS".equals(category) || "SKIRT".equals(category)
            || "DRESS".equals(category) || "OUTER".equals(category) || "SHOES".equals(category)
            || "HAT".equals(category) || "BAG".equals(category) || "ACCESSORY".equals(category);
    }

    record ValidationResult(ValidationFailureReason reason) {
        static ValidationResult valid() {
            return new ValidationResult(null);
        }

        static ValidationResult invalid(ValidationFailureReason reason) {
            return new ValidationResult(reason);
        }

        boolean isValid() {
            return reason == null;
        }
    }
}
