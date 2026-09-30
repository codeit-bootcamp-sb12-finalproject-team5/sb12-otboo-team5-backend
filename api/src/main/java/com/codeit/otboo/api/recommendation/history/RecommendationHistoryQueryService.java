package com.codeit.otboo.api.recommendation.history;

import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.recommendation.entity.RecommendationOutfitHistory;
import com.codeit.otboo.domain.recommendation.entity.RecommendationRequestHistory;
import com.codeit.otboo.domain.recommendation.repository.RecommendationOutfitClothesRepository;
import com.codeit.otboo.domain.recommendation.repository.RecommendationOutfitHistoryRepository;
import com.codeit.otboo.domain.recommendation.repository.RecommendationRequestHistoryRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationHistoryQueryService {
    private final RecommendationRequestHistoryRepository requestHistoryRepository;
    private final RecommendationOutfitHistoryRepository outfitHistoryRepository;
    private final RecommendationOutfitClothesRepository outfitClothesRepository;

    // 최근 추천 이력 10개 조회
    public RecommendationHistoryContext recentExposureContext(UUID userId, RecommendationType recommendationType) {
        List<RecommendationRequestHistory> requests = requestHistoryRepository
            .findByUser_IdAndRecommendationTypeOrderByRequestedAtDesc(userId, recommendationType,
                PageRequest.of(0, RecommendationHistoryPolicy.RECENT_HISTORY_LIMIT))
            .getContent();
        if (requests.isEmpty()) return RecommendationHistoryContext.empty();

        Map<UUID, Integer> requestOrder = new HashMap<>();
        for (int index = 0; index < requests.size(); index++) requestOrder.put(requests.get(index).getId(), index);

        List<RecommendationOutfitHistory> outfits = outfitHistoryRepository
            .findAllByRecommendationRequestHistory_IdIn(requestOrder.keySet());
        if (outfits.isEmpty()) return RecommendationHistoryContext.empty();

        Map<UUID, UUID> requestIdByOutfitId = outfits.stream().collect(Collectors.toMap(
            RecommendationOutfitHistory::getId,
            outfit -> outfit.getRecommendationRequestHistory().getId()
        ));
        Map<UUID, Integer> recent3 = new HashMap<>();
        Map<UUID, Integer> older = new HashMap<>();
        outfitClothesRepository.findAllByRecommendationOutfitHistory_IdIn(requestIdByOutfitId.keySet())
            .forEach(link -> {
                Integer order = requestOrder.get(requestIdByOutfitId.get(link.getRecommendationOutfitHistory().getId()));
                if (order == null) return;
                Map<UUID, Integer> counts = order < RecommendationHistoryPolicy.STRONG_HISTORY_WINDOW ? recent3 : older;
                counts.merge(link.getClothes().getId(), 1, Integer::sum);
            });

        return new RecommendationHistoryContext(recent3, older);
    }

    public Set<String> recentOutfitFingerprints(UUID userId, RecommendationType recommendationType) {
        List<RecommendationRequestHistory> requests = requestHistoryRepository
            .findByUser_IdAndRecommendationTypeOrderByRequestedAtDesc(userId, recommendationType,
                PageRequest.of(0, RecommendationHistoryPolicy.RECENT_HISTORY_LIMIT))
            .getContent();
        if (requests.isEmpty()) return Set.of();
        return outfitHistoryRepository.findAllByRecommendationRequestHistory_IdIn(requests.stream()
                .map(RecommendationRequestHistory::getId).toList())
            .stream().map(RecommendationOutfitHistory::getOutfitFingerprint).collect(Collectors.toUnmodifiableSet());
    }
}
