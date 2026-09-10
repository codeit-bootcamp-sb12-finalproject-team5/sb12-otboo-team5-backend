package com.codeit.otboo.support.weather.dto.response;

public record KakaoRegionDto(
    String regionType,
    String code,
    String addressName,
    String region1DepthName,
    String region2DepthName,
    String region3DepthName,
    String region4DepthName,
    double longitude,
    double latitude
) {}
