package com.codeit.otboo.api.profile.controller;

import com.codeit.otboo.api.common.security.CustomUserDetails;
import com.codeit.otboo.api.profile.dto.request.ProfileUpdateRequest;
import com.codeit.otboo.api.profile.dto.response.ProfileDto;
import com.codeit.otboo.api.profile.service.ProfileService;
import com.codeit.otboo.api.weather.dto.response.WeatherDto;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users/{userId}/profiles")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @GetMapping
  public ResponseEntity<ProfileDto> getProfile(
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    if (!userId.equals(userDetails.getUserId())) {
      throw ProfileException.accessDenied();
    }
    return ResponseEntity.ok(profileService.getProfile(userId));
  }

  @PatchMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ProfileDto> updateProfile(
      @PathVariable UUID userId,
      @RequestPart("request") ProfileUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    if (!userId.equals(userDetails.getUserId())) {
      throw ProfileException.accessDenied();
    }
    return ResponseEntity.ok(profileService.updateProfile(userId, request, image));
  }

  @GetMapping("/weather")
  public ResponseEntity<List<WeatherDto>> getWeather(
      @PathVariable UUID userId,
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    if (!userId.equals(userDetails.getUserId())) {
      throw ProfileException.accessDenied();
    }
    return ResponseEntity.ok(profileService.getWeather(userId));
  }

}
