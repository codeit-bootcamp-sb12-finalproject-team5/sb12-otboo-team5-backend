package com.codeit.otboo.api.feed.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedUpdateRequest(
        @Pattern(regexp = ".*\\S.*")
        @Size(max = 10_000)
        String content,
        Boolean isVisible
) {
}
