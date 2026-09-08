package com.codeit.otboo.support.weather.dto.response;

import java.time.LocalDateTime;

/** 기상청 단기예보의 시각·항목·값 한 건을 전달하는 DTO입니다. */
public record KmaForecastPointDto(
    LocalDateTime forecastAt,
    String category,
    String value
) {}
