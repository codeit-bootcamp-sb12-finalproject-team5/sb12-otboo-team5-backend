package com.codeit.otboo.api.recommendation.history;

import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.recommendation.RecommendationType;
import com.codeit.otboo.domain.recommendation.exception.RecommendationException;
import com.codeit.otboo.domain.recommendation.repository.RecommendationRequestHistoryRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendationDailyLimitPolicy {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int OOTD_DAILY_LIMIT = 100;
    private static final int OUTFIT_DAILY_LIMIT = 100;

    private final RecommendationRequestHistoryRepository requestHistoryRepository;

    public void validateAvailable(UUID userId, RecommendationType type) {
        Usage usage = usage(userId, type);
        if (usage.remaining() <= 0) {
            throw new RecommendationException(ErrorCode.RECOMMENDATION_DAILY_LIMIT_EXCEEDED, null)
                .addDetail("limit", usage.limit())
                .addDetail("remaining", usage.remaining());
        }
    }

    public Usage usage(UUID userId, RecommendationType type) {
        ZonedDateTime start = ZonedDateTime.now(SERVICE_ZONE).toLocalDate().atStartOfDay(SERVICE_ZONE);
        OffsetDateTime startAt = start.toOffsetDateTime();
        OffsetDateTime endAt = start.plusDays(1).toOffsetDateTime();

        int limit = limit(type);
        int used = Math.toIntExact(requestHistoryRepository.countByUserAndTypeAndRequestedAtBetween(userId, type, startAt, endAt));

        return new Usage(limit, used, Math.max(0, limit - used));
    }

    private int limit(RecommendationType type) {
        return type == RecommendationType.OOTD ? OOTD_DAILY_LIMIT : OUTFIT_DAILY_LIMIT;
    }

    public record Usage(int limit, int used, int remaining) {
    }
}
