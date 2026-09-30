package com.codeit.otboo.api.feed.dto;

import java.util.UUID;

public record AuthorResponse(
        UUID userId,
        String name,
        String profileImageUrl
) {}
