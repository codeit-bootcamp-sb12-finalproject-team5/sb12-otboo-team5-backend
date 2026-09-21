package com.codeit.otboo.api.feed.dto;

import com.codeit.otboo.domain.clothes.entity.Clothes;
import com.codeit.otboo.domain.feed.entity.Feed;
import com.codeit.otboo.domain.outfit.entity.Ootd;
import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;

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
        OotdWeatherResponse weather,
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
            OotdWeatherResponse.of(ootd),
            clothes.stream()
                .map(clothesItem -> ClothesSimpleResponse.of(
                    clothesItem, clothesImageUrlResolver.apply(clothesItem)))
                .toList(),
            feed.getContent(), feed.getLikeCount(), feed.getCommentCount(), likedByMe
        );
    }

    public record OotdWeatherResponse(
            SkyStatus skyStatus,
            OotdWeatherPrecipitationResponse precipitation,
            OotdWeatherTemperatureResponse temperature
    ) {
        public static OotdWeatherResponse of(Ootd ootd) {
            if (ootd == null) {
                return null;
            }
            return new OotdWeatherResponse(
                ootd.getSkyStatus(),
                new OotdWeatherPrecipitationResponse(
                    ootd.getPrecipitationType(),
                    ootd.getPrecipitationAmount(),
                    ootd.getPrecipitationProbability()
                ),
                new OotdWeatherTemperatureResponse(
                    ootd.getTemperatureCurrent(),
                    ootd.getTemperatureComparedToDayBefore(),
                    ootd.getTemperatureMin(),
                    ootd.getTemperatureMax()
                )
            );
        }

        public record OotdWeatherPrecipitationResponse(
                PrecipitationType type,
                BigDecimal amount,
                BigDecimal probability
        ) {}
        public record OotdWeatherTemperatureResponse(
                BigDecimal current,
                BigDecimal comparedToDayBefore,
                BigDecimal min,
                BigDecimal max
        ) {}
    }

}
