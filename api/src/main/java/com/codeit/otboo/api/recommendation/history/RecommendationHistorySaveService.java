package com.codeit.otboo.api.recommendation.history;

import com.codeit.otboo.api.recommendation.llm.LlmRecommendationResponse;
import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.clothes.repository.ClothesRepository;
import com.codeit.otboo.domain.recommendation.OutfitFingerprintGenerator;
import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.recommendation.entity.RecommendationOutfitClothes;
import com.codeit.otboo.domain.recommendation.entity.RecommendationOutfitHistory;
import com.codeit.otboo.domain.recommendation.entity.RecommendationRequestHistory;
import com.codeit.otboo.domain.recommendation.entity.RecommendationRequestSelectedClothes;
import com.codeit.otboo.domain.recommendation.repository.RecommendationOutfitClothesRepository;
import com.codeit.otboo.domain.recommendation.repository.RecommendationOutfitHistoryRepository;
import com.codeit.otboo.domain.recommendation.repository.RecommendationRequestHistoryRepository;
import com.codeit.otboo.domain.recommendation.repository.RecommendationRequestSelectedClothesRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecommendationHistorySaveService {
    private static final String ALGORITHM_VERSION = "content-based-history-penalty-v1";

    private final UserRepository userRepository;
    private final ClothesRepository clothesRepository;
    private final RecommendationRequestHistoryRepository requestHistoryRepository;
    private final RecommendationRequestSelectedClothesRepository requestSelectedClothesRepository;
    private final RecommendationOutfitHistoryRepository outfitHistoryRepository;
    private final RecommendationOutfitClothesRepository outfitClothesRepository;

    @Transactional
    public void save(
        UUID userId, UUID weatherId, RecommendationType type, String promptVersion,
        List<Clothes> selectedClothes, LlmRecommendationResponse recommendation
    ) {
        User user = userRepository.findById(userId).orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        RecommendationRequestHistory request = requestHistoryRepository.save(new RecommendationRequestHistory(
            user, type, weatherId, ALGORITHM_VERSION, promptVersion, now));
        requestSelectedClothesRepository.saveAll(selectedClothes.stream()
            .map(clothes -> new RecommendationRequestSelectedClothes(request, clothes))
            .toList());

        Map<UUID, Clothes> clothesById = clothesRepository.findAllById(recommendation.outfits().stream()
                .flatMap(outfit -> outfit.clothesIds().stream()).distinct().toList())
            .stream().collect(java.util.stream.Collectors.toMap(Clothes::getId, Function.identity()));
        for (LlmRecommendationResponse.GeneratedOutfit outfit : recommendation.outfits()) {
            RecommendationOutfitHistory outfitHistory = outfitHistoryRepository.save(new RecommendationOutfitHistory(
                request, outfit.rank(), OutfitFingerprintGenerator.generate(outfit.clothesIds()), outfit.reason(), now));
            outfitClothesRepository.saveAll(outfit.clothesIds().stream()
                .map(clothesById::get)
                .map(clothes -> new RecommendationOutfitClothes(outfitHistory, clothes))
                .toList());
        }
    }

}
