package com.codeit.otboo.api.feed.dto;

import com.codeit.otboo.domain.feed.enums.SortDirection;
import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record FeedSearchRequest(
        String cursor,
        UUID idAfter,
        @NotNull
        @Min(1)
        Integer limit,
        @NotBlank
        @Pattern(regexp = "createdAt|likeCount")
        String sortBy,
        @NotNull
        SortDirection sortDirection,
        String keywordLike,
        SkyStatus skyStatusEqual,
        PrecipitationType precipitationTypeEqual,
        UUID authorIdEqual
) {
}
