package com.codeit.otboo.api.weather.service.Impl;

import com.codeit.otboo.api.weather.dto.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.WeatherDto;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.api.weather.service.WeatherService;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.weather.dto.GridCoordinate;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {
    private final LocationService locationService;

    @Override
    public List<WeatherDto> findWeather(Double longitude, Double latitude) {
        // KMA 연동 모듈 확정 후 예보 조회를 구현한다.
        return List.of();
    }

    @Override
    public WeatherAPILocation findLocation(Double longitude, Double latitude) {
        if (longitude == null || latitude == null) {
            throw new WeatherException(ErrorCode.INVALID_INPUT_VALUE);
        }
        GridCoordinate coordinate = locationService.convert(longitude, latitude);
        WeatherGrid grid = locationService.findOrCreate(
            coordinate.x(), coordinate.y(), longitude, latitude);
        return new WeatherAPILocation(latitude, longitude, grid.getNx(), grid.getNy(),
                grid.getLocationNames());
    }
}
