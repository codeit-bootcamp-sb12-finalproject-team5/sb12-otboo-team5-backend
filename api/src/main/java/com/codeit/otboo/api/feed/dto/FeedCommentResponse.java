package com.codeit.otboo.api.feed.dto;

import com.codeit.otboo.domain.feed.entity.FeedComment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FeedCommentResponse(
        UUID id,
        OffsetDateTime createdAt,
        UUID feedId,
        AuthorResponse author,
        String content
) {
    public static FeedCommentResponse of(FeedComment comment, String profileImageUrl) {
        return new FeedCommentResponse(
            comment.getId(),
            comment.getCreatedAt(),
            comment.getFeed().getId(),
            new AuthorResponse(
                comment.getUser().getId(),
                comment.getUser().getName(),
                profileImageUrl
            ),
            comment.getContent()
        );
    }
}
