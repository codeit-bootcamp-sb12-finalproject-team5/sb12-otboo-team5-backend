package com.codeit.otboo.api.weather.dto.response;

import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import java.util.List;
import java.util.UUID;

/** 격자 캐시에 저장하는 불변 조회 값입니다. */
public record WeatherGridDto(UUID id, int nx, int ny, List<String> locationNames) {
    public WeatherGridDto {
        locationNames = List.copyOf(locationNames);
    }

    public static WeatherGridDto from(WeatherGrid grid) {
        return new WeatherGridDto(grid.getId(), grid.getNx(), grid.getNy(), grid.getLocationNames());
    }
}
