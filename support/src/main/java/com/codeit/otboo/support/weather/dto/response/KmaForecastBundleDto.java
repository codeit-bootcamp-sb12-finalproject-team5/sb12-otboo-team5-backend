package com.codeit.otboo.support.weather.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** 한 발표 시각의 기상청 단기예보 항목 전체를 전달하는 DTO입니다. */
public record KmaForecastBundleDto(
        LocalDateTime forecastedAt,
        int nx,
        int ny,
        List<KmaForecastPointDto> points
) {}
