package com.codeit.otboo.api.follow.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.follow.dto.response.FollowUserDto;
import com.codeit.otboo.api.follow.service.FollowService;
import com.codeit.otboo.domain.common.dto.CursorResponse;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
public class FollowController {

  private final FollowService followService;

  @GetMapping("/followings")
  public ResponseEntity<CursorResponse<FollowUserDto>> getFollowings(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam UUID followerId,
      @RequestParam int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter
  ) {
    if (!followerId.equals(userDetails.getUserId())) {
      throw ProfileException.accessDenied();
    }
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
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @RequestParam UUID followeeId,
      @RequestParam int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter
  ) {
    if (!followeeId.equals(userDetails.getUserId())) {
      throw ProfileException.accessDenied();
    }
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

}
