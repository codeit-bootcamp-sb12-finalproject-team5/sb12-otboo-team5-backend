package com.codeit.otboo.api.feed;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.feed.dto.*;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedController {

    private final FeedService feedService;

    @PostMapping
    public ResponseEntity<FeedResponse> post(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody FeedRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(feedService.create(userDetails.getUserId(), request));
    }

    @GetMapping
    public ResponseEntity<CursorResponse<FeedResponse>> search(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @ModelAttribute FeedSearchRequest request
    ) {
        return ResponseEntity.ok(feedService.readAll(userDetails.getUserId(), request));
    }

    @PatchMapping("/{feedId}")
    public ResponseEntity<FeedResponse> patch(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID feedId,
        @Valid @RequestBody FeedUpdateRequest request
    ) {
        return ResponseEntity.ok(feedService.update(userDetails.getUserId(), feedId, request));
    }

    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> softDelete(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID feedId
    ) {
        feedService.softDelete(userDetails.getUserId(), feedId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{feedId}/like")
    public ResponseEntity<Void> postLike(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID feedId
    ) {
        feedService.createLike(userDetails.getUserId(), feedId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> deleteLike(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID feedId
    ) {
        feedService.deleteLike(userDetails.getUserId(), feedId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{feedId}/comments")
    public ResponseEntity<FeedCommentResponse> postComment(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable UUID feedId,
        @Valid @RequestBody FeedCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(feedService.createComment(userDetails.getUserId(), feedId, request));
    }

    @GetMapping("/{feedId}/comments")
    public ResponseEntity<CursorResponse<FeedCommentResponse>> searchComment(
        @PathVariable UUID feedId,
        @Valid @ModelAttribute FeedCommentSearchRequest request
    ) {
        return ResponseEntity.ok(feedService.readAllComment(feedId, request));
    }

}
