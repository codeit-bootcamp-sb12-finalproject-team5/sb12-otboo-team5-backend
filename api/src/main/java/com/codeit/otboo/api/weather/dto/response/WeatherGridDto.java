package com.codeit.otboo.api.weather.dto.response;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import java.util.List;
import java.util.UUID;

public record WeatherGridDto(
    UUID id,
    int nx,
    int ny,
    List<String> locationNames
) {
    public WeatherGridDto {
        locationNames = List.copyOf(locationNames);
    }

    public boolean hasAdministrativeRegion() {
        return !locationNames.isEmpty() && !locationNames.get(0).isBlank();
    }

    public static WeatherGridDto from(WeatherGrid grid) {
        return new WeatherGridDto(
            grid.getId(),
            grid.getNx(),
            grid.getNy(),
            grid.getLocationNames()
        );
    }
}
