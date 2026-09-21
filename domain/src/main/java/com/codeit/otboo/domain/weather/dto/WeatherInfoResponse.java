package com.codeit.otboo.domain.weather.dto;

import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import java.math.BigDecimal;

/**
 * ootd 테이블에 그대로 넣을 수 있도록 평평하게 편 날씨 스냅샷.
 *
 * <p>필드가 ootd 컬럼과 1:1로 대응한다. 저장하는 쪽에서 계산할 것이 남지 않게 하려는 의도다.
 * ootd는 {@code temperatureComparedToDayBefore}만 NULL을 허용하므로, 나머지는 채워서 보낸다.
 */
public record WeatherInfoResponse(
    SkyStatus skyStatus,
    PrecipitationType precipitationType,
    BigDecimal precipitationAmount,
    BigDecimal precipitationProbability,
    BigDecimal temperatureCurrent,
    /** 전일 대비 기온차. 비교할 전일 데이터가 없으면 null. */
    BigDecimal temperatureComparedToDayBefore,
    BigDecimal temperatureMin,
    BigDecimal temperatureMax
) {
}
