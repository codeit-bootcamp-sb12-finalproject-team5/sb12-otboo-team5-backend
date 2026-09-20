package com.codeit.otboo.api.feed.dto;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.feed.enums.PrecipitationType;
import com.codeit.otboo.domain.feed.enums.SkyStatus;
import com.codeit.otboo.domain.outfit.entity.Ootd;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public record FeedResponse(
        UUID id,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        AuthorResponse author,
        FeedWeatherResponse weather,
        List<ClothesSimpleResponse> ootds,
        String content,
        Long likeCount,
        Long commentCount,
        Boolean likedByMe
) {
    public static FeedResponse of(
        Feed feed,
        List<Clothes> clothes,
        String profileImageUrl,
        Ootd ootd,
        boolean likedByMe,
        Function<Clothes, String> clothesImageUrlResolver
    ) {
        return new FeedResponse(
            feed.getId(), feed.getCreatedAt(), feed.getUpdatedAt(),
            new AuthorResponse(feed.getUser().getId(), feed.getUser().getName(), profileImageUrl),
            FeedWeatherResponse.of(ootd),
            clothes.stream()
                .map(clothesItem -> ClothesSimpleResponse.of(
                    clothesItem, clothesImageUrlResolver.apply(clothesItem)))
                .toList(),
            feed.getContent(), feed.getLikeCount(), feed.getCommentCount(), likedByMe
        );
    }

    public record FeedWeatherResponse(
            SkyStatus skyStatus,
            FeedWeatherPrecipitationResponse precipitation,
            FeedWeatherTemperatureResponse temperature
    ) {
        public static FeedWeatherResponse of(Ootd ootd) {
            if (ootd == null) {
                return null;
            }
            return new FeedWeatherResponse(
                ootd.getSkyStatus(),
                new FeedWeatherPrecipitationResponse(
                    ootd.getPrecipitationType(),
                    ootd.getPrecipitationAmount(),
                    ootd.getPrecipitationProbability()
                ),
                new FeedWeatherTemperatureResponse(
                    ootd.getTemperatureCurrent(),
                    ootd.getTemperatureComparedToDayBefore(),
                    ootd.getTemperatureMin(),
                    ootd.getTemperatureMax()
                )
            );
        }

        public record FeedWeatherPrecipitationResponse(
                PrecipitationType type,
                BigDecimal amount,
                BigDecimal probability
        ) {}
        public record FeedWeatherTemperatureResponse(
                BigDecimal current,
                BigDecimal comparedToDayBefore,
                BigDecimal min,
                BigDecimal max
        ) {}
    }

}
