package com.codeit.otboo.api.profile.service;

import com.codeit.otboo.api.profile.dto.request.ProfileUpdateRequest;
import com.codeit.otboo.api.profile.dto.response.ProfileDto;
import com.codeit.otboo.api.weather.dto.response.WeatherGridDto;
import com.codeit.otboo.api.weather.service.WeatherService;
import com.codeit.otboo.domain.profile.entity.Profile;
import com.codeit.otboo.domain.profile.exception.ProfileException;
import com.codeit.otboo.domain.profile.repository.ProfileRepository;
import com.codeit.otboo.domain.user.entity.User;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import com.codeit.otboo.domain.weather.repository.WeatherGridRepository;
import com.codeit.otboo.support.storage.S3StorageService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProfileService {

  private final ProfileRepository profileRepository;
  private final WeatherService weatherService;
  private final WeatherGridRepository weatherGridRepository;
  private final S3StorageService s3StorageService;

  @Transactional
  public Profile createProfile(User user) {

    Float[] preferenceVector = new Float[1536];
    Arrays.fill(preferenceVector, 0f);

    Profile profile = Profile.builder()
        .user(user)
        .preferenceVector(preferenceVector)
        .profileImageUrl("profile/default.png")
        .temperatureSensitivity((short) 0)
        .build();

    profileRepository.save(profile);

    return profile;
  }

  @Transactional(readOnly = true)
  public ProfileDto getProfile(UUID userId) {
    Profile profile = profileRepository.findByUser_Id(userId)
        .orElseThrow(ProfileException::notFound);
    String profileImageUrl = s3StorageService.getPresignedUrl(profile.getProfileImageUrl());
    return ProfileDto.from(profile, profileImageUrl);
  }

  @Transactional
  public ProfileDto updateProfile(
      UUID userId,
      ProfileUpdateRequest request,
      MultipartFile image
  ) {
    Profile profile = profileRepository.findByUser_Id(userId)
        .orElseThrow(ProfileException::notFound);

    User user = profile.getUser();
    if (request.name() != null) {
      user.setName(request.name());
    }

    if (request.gender() != null) {
      profile.updateGender(request.gender());
    }
    if (request.birthDate() != null) {
      profile.updateBirthDate(request.birthDate());
    }
    if (request.temperatureSensitivity() != null) {
      profile.updateTemperatureSensitivity(request.temperatureSensitivity());
    }

    if (request.longitude() != null && request.latitude() != null && request.locationSource() != null) {
      WeatherGridDto weatherGridDto = weatherService.findGrid(request.longitude(), request.latitude());
      WeatherGrid weatherGrid = weatherGridRepository.findById(weatherGridDto.id())
              .orElseThrow(ProfileException::resourceNotFound);
      profile.updateLocation(weatherGrid, request.locationSource());
    }
    if (image != null && !image.isEmpty()) {
      String oldObjectKey = profile.getProfileImageUrl();
      if (oldObjectKey != null && !oldObjectKey.equals("profile/default.png")) {
        s3StorageService.deleteOne(oldObjectKey);
      }
      String objectKey = s3StorageService.saveProfile(image, userId);
      profile.updateProfileImageUrl(objectKey);
    }
    String profileImageUrl = s3StorageService.getPresignedUrl(profile.getProfileImageUrl());
    return ProfileDto.from(profile, profileImageUrl);
  }

}
