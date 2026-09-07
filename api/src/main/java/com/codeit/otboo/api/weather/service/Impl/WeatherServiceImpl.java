package com.codeit.otboo.api.weather.service.Impl;

import com.codeit.otboo.api.weather.dto.response.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.response.WeatherDto;
import com.codeit.otboo.api.weather.dto.request.LocationReadRequest;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.api.weather.service.WeatherService;
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
    public List<WeatherDto> findWeather(WeatherReadRequest request) {
        // KMA 연동 모듈 확정 후 예보 조회를 구현한다.
        return List.of();
    }

    @Override
    public WeatherAPILocation findLocation(LocationReadRequest request) {
        double longitude = request.longitude();
        double latitude = request.latitude();
        GridCoordinate coordinate = locationService.convert(longitude, latitude);
        WeatherGrid grid = locationService.findOrCreate(
            coordinate.x(), coordinate.y(), longitude, latitude);
        return new WeatherAPILocation(latitude, longitude, grid.getNx(), grid.getNy(),
                grid.getLocationNames());
    }
}
