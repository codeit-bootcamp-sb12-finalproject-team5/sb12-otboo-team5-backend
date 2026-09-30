package com.codeit.otboo.api.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FeedCommentRequest(
        @NotNull
        UUID feedId,
        @NotNull
        UUID authorId,
        @NotBlank
        String content
) {
}
