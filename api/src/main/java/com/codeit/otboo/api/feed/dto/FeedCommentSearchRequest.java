package com.codeit.otboo.api.feed.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public record FeedCommentSearchRequest(
        @NotNull
        UUID feedId,
        String cursor,
        UUID idAfter,
        @NotNull
        @Min(1)
        @Max(100)
        Integer limit
) {
}
