package com.codeit.otboo.api.weather.service;

import org.springframework.stereotype.Service;

import com.codeit.otboo.domain.weather.dto.GridCoordinate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

	public GridCoordinate convert(double longitude, double latitude) {
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
		if (theta > Math.PI) theta -= 2.0 * Math.PI;
		if (theta < -Math.PI) theta += 2.0 * Math.PI;
		theta *= sn;

		GridCoordinate grid = new GridCoordinate(
			(int) Math.floor(ra * Math.sin(theta) + 43.0 + 0.5),
			(int) Math.floor(ro - ra * Math.cos(theta) + 136.0 + 0.5));
		log.info("[GRID] 좌표 변환 완료 longitude={}, latitude={} -> nx={}, ny={}",
			longitude, latitude, grid.x(), grid.y());
		return grid;
	}
}
