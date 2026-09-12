package com.codeit.otboo.api.weather.service.Impl;

import com.codeit.otboo.api.weather.dto.request.LocationReadRequest;
import com.codeit.otboo.api.weather.dto.request.WeatherReadRequest;
import com.codeit.otboo.api.weather.dto.response.WeatherAPILocation;
import com.codeit.otboo.api.weather.dto.response.WeatherDto;
import com.codeit.otboo.api.weather.dto.response.WeatherViewData;
import com.codeit.otboo.api.weather.service.LocationService;
import com.codeit.otboo.api.weather.service.WeatherService;
import com.codeit.otboo.api.weather.service.WeatherViewCacheService;
import com.codeit.otboo.api.weather.dto.response.WeatherGridDto;
import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WeatherServiceImpl implements WeatherService {
    private final LocationService locationService;
    private final WeatherViewCacheService weatherViewCacheService;

    @Override
    public List<WeatherDto> findWeather(WeatherReadRequest request) {
        WeatherGridDto grid = findGrid(request.longitude(), request.latitude());
        OffsetDateTime targetAt = forecastSlotForToday(OffsetDateTime.now(KmaTimeCalculator.KST));

        List<WeatherViewData> views = weatherViewCacheService.findWeatherView(grid, targetAt);
        WeatherAPILocation location = toLocationResponse(grid, request.longitude(), request.latitude());

        return views.stream()
            .map(view -> new WeatherDto(
                view.id(),
                view.forecastedAt(),
                view.forecastAt(),
                location,
                view.skyStatus(),
                view.precipitation(),
                view.humidity(),
                view.temperature(),
                view.windSpeed()
            ))
            .toList();
    }

    @Override
    public WeatherAPILocation findLocation(LocationReadRequest request) {
        WeatherGridDto grid = findGrid(request.longitude(), request.latitude());
        return toLocationResponse(grid, request.longitude(), request.latitude());
    }

    @Override
    public WeatherGridDto findGrid(double longitude, double latitude) {
        var coordinate = locationService.convert(longitude, latitude);
        return locationService.findOrCreate(coordinate.x(), coordinate.y(), longitude, latitude);
    }

    private OffsetDateTime forecastSlotForToday(OffsetDateTime now) {
        OffsetDateTime hour = KmaTimeCalculator.normalizeToKstHour(now);
        // 늦은 저녁에도 오늘을 첫 예보 날짜로 유지합니다.
        if (hour.getHour() >= 21) return hour.withHour(21);
        if (now.isEqual(hour) && hour.getHour() % 3 == 0) return hour;
        return hour.plusHours(3 - hour.getHour() % 3);
    }

    private WeatherAPILocation toLocationResponse(WeatherGridDto grid, double longitude, double latitude) {
        return new WeatherAPILocation(latitude, longitude, grid.nx(), grid.ny(), grid.locationNames());
    }
}
