package com.codeit.otboo.api.feed.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FeedRequest(
        @NotNull
        UUID authorId,
        @Nullable
        UUID weatherId,
        @NotNull
        UUID outfitId,
        @Nullable
        String content
) {
}
