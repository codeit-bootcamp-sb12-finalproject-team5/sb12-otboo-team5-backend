package com.codeit.otboo.api.profile.dto.response;

import com.codeit.otboo.domain.profile.entity.Gender;
import com.codeit.otboo.domain.profile.entity.LocationSource;
import com.codeit.otboo.domain.profile.entity.Profile;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfileDto(
    UUID userId,
    String name,
    Gender gender,
    LocalDate birthDate,
    List<String> locationNames,
    LocationSource locationSource,
    Short temperatureSensitivity,
    String profileImageUrl
) {
  public static ProfileDto from(Profile profile) {
    return new ProfileDto(
        profile.getUser().getId(),
        profile.getUser().getName(),
        profile.getGender(),
        profile.getBirthDate(),
        profile.getWeatherGrid() != null
            ? profile.getWeatherGrid().getLocationNames()
            : List.of(),
        profile.getLocationSource(),
        profile.getTemperatureSensitivity(),
        profile.getProfileImageUrl()
    );
  }
}
