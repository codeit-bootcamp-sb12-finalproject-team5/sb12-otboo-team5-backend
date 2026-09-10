package com.codeit.otboo.api.weather.service;

import com.codeit.otboo.support.weather.client.KakaoClient;
import com.codeit.otboo.support.weather.dto.response.KakaoRegionDto;
import com.codeit.otboo.api.weather.exception.WeatherException;
import com.codeit.otboo.api.weather.repository.WeatherRepository;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import com.codeit.otboo.domain.weather.dto.GridCoordinate;
import com.codeit.otboo.domain.weather.entity.WeatherGrid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final WeatherRepository weatherRepository;
    private final KakaoClient kakaoClient;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public WeatherGrid findOrCreate(int nx, int ny, double longitude, double latitude) {

        var existingGrid = weatherRepository.findGrid(nx, ny);

        if (existingGrid.isPresent()) {
            WeatherGrid grid = existingGrid.get();

            log.info("[LOCATION] 기존 격자 사용 gridId={}, nx={}, ny={}, Kakao 호출=false",
                grid.getId(), grid.getNx(), grid.getNy());

            return grid;
        }

        log.info("[LOCATION] 신규 격자 nx={}, ny={}, Kakao 행정동 조회를 시작합니다.", nx, ny);

        List<String> names = kakaoClient.findAdministrativeRegion(longitude, latitude)
            .map(this::regionNames)
            .orElseGet(List::of);

        WeatherGrid grid = weatherRepository.findOrCreateGrid(nx, ny, names);

        log.info("[LOCATION] 신규 격자 저장 완료 gridId={}, nx={}, ny={}, locationNames={}",
            grid.getId(), grid.getNx(), grid.getNy(), grid.getLocationNames());

        return grid;
    }

    private List<String> regionNames(KakaoRegionDto region) {
        return List.of(
            region.region1DepthName(),
            region.region2DepthName(),
            region.region3DepthName(),
            region.region4DepthName()
        );
    }

    public GridCoordinate convert(double longitude, double latitude) {

        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude <= -90 || latitude >= 90) {
            throw new WeatherException(ErrorCode.INVALID_LOCATION_INPUT);
        }

        double re = 6371.00877 / 5.0;
        double slat1 = Math.toRadians(30.0);
        double slat2 = Math.toRadians(60.0);
        double olon = Math.toRadians(126.0);
        double olat = Math.toRadians(38.0);
        double sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) /
            Math.log(Math.tan(Math.PI * 0.25 + slat2 * 0.5) /
                Math.tan(Math.PI * 0.25 + slat1 * 0.5));
        double sf = Math.pow(Math.tan(Math.PI * 0.25 + slat1 * 0.5), sn) * Math.cos(slat1) / sn;
        double ro = re * sf / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);
        double ra = re * sf /
            Math.pow(Math.tan(Math.PI * 0.25 + Math.toRadians(latitude) * 0.5), sn);
        double theta = Math.toRadians(longitude) - olon;

        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }

        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }

        theta *= sn;

        GridCoordinate grid = new GridCoordinate(
            (int) Math.floor(ra * Math.sin(theta) + 43.0 + 0.5),
            (int) Math.floor(ro - ra * Math.cos(theta) + 136.0 + 0.5)
        );

        log.info("[GRID] 좌표 변환 완료 longitude={}, latitude={} -> nx={}, ny={}",
            longitude, latitude, grid.x(), grid.y());

        return grid;
    }
}
