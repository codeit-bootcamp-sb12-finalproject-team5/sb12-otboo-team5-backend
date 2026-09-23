package com.codeit.otboo.api.follow.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.follow.dto.request.FollowCreateRequest;
import com.codeit.otboo.api.follow.dto.response.FollowDto;
import com.codeit.otboo.api.follow.dto.response.FollowSummaryDto;
import com.codeit.otboo.api.follow.dto.response.FollowUserDto;
import com.codeit.otboo.api.follow.service.FollowService;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowService followService;

  @PostMapping
  public ResponseEntity<FollowDto> createFollow(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody FollowCreateRequest request
  ) {
    if (!request.followerId().equals(userDetails.getUserId())) {
      throw ProfileException.accessDenied();
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(followService.createFollow(request));
  }

  @GetMapping("/followings")
  public ResponseEntity<CursorResponse<FollowUserDto>> getFollowings(
      @RequestParam UUID followerId,
      @RequestParam int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter
  ) {
    return ResponseEntity.ok(
        followService.findFolloweesByFollowerId(
            followerId,
            cursor,
            idAfter,
            limit,
            Sort.Direction.DESC
        )
    );
  }

  @GetMapping("/followers")
  public ResponseEntity<CursorResponse<FollowUserDto>> getFollowers(
      @RequestParam UUID followeeId,
      @RequestParam int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter
  ) {
    return ResponseEntity.ok(
        followService.findFollowersByFolloweeId(
            followeeId,
            cursor,
            idAfter,
            limit,
            Sort.Direction.DESC
        )
    );
  }

  @GetMapping("/summary")
  public ResponseEntity<FollowSummaryDto> getFollowSummary(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam UUID userId
  ) {
    return ResponseEntity.ok(
        followService.findFollowSummary(
            userId,
            userDetails.getUserId()
        )
    );
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> deleteFollow(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @PathVariable UUID followId
  ) {
    followService.deleteFollow(
        followId,
        userDetails.getUserId()
    );

    return ResponseEntity.noContent().build();
  }

}
