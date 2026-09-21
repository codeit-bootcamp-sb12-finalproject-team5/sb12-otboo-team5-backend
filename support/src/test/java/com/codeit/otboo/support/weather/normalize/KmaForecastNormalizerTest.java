package com.codeit.otboo.support.weather.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.otboo.domain.weather.entity.PrecipitationType;
import com.codeit.otboo.domain.weather.entity.SkyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KmaForecastNormalizerTest {

    @ParameterizedTest(name = "PTY {0} -> {1}")
    @CsvSource({
            "0, NONE",       // 없음
            "1, RAIN",       // 비
            "2, RAIN_SNOW",  // 비/눈 = 진눈깨비
            "3, SNOW",       // 눈
            "4, SHOWER",     // 소나기 (단기예보 전용)
            "5, RAIN",       // 빗방울 (초단기실황 전용)
            "6, RAIN_SNOW",  // 빗방울눈날림 (초단기실황 전용)
            "7, SNOW",       // 눈날림 (초단기실황 전용)
    })
    void mapsEveryKmaPrecipitationCode(int code, PrecipitationType expected) {
        assertThat(KmaForecastNormalizer.precipitationType(code)).isEqualTo(expected);
    }

    // 소나기를 비로 뭉개면 "지나가는 비"와 "종일 비"를 구분하지 못한다. 옷 추천이 달라지는 지점이다.
    @Test
    void treatsShowerAsItsOwnTypeInsteadOfPlainRain() {
        assertThat(KmaForecastNormalizer.precipitationType(4))
                .isEqualTo(PrecipitationType.SHOWER)
                .isNotEqualTo(PrecipitationType.RAIN);
    }

    // 규격에 없는 값이 오더라도 예외로 죽지 않고 "강수 없음"으로 떨어져야 한다.
    @ParameterizedTest
    @CsvSource({"-1", "8", "99"})
    void fallsBackToNoneForCodesOutsideTheSpec(int code) {
        assertThat(KmaForecastNormalizer.precipitationType(code)).isEqualTo(PrecipitationType.NONE);
    }

    @ParameterizedTest(name = "SKY {0} -> {1}")
    @CsvSource({
            "1, CLEAR",           // 맑음
            "3, MOSTLY_CLOUDY",   // 구름많음
            "4, CLOUDY",          // 흐림
    })
    void mapsEveryKmaSkyCode(int code, SkyStatus expected) {
        assertThat(KmaForecastNormalizer.skyStatus(code)).isEqualTo(expected);
    }

    // 2번은 기상청 규격에 없는 코드다. 맑음으로 보면 실제보다 좋은 날씨로 안내하게 되므로 흐림으로 둔다.
    @ParameterizedTest
    @CsvSource({"0", "2", "9"})
    void fallsBackToCloudyForSkyCodesOutsideTheSpec(int code) {
        assertThat(KmaForecastNormalizer.skyStatus(code)).isEqualTo(SkyStatus.CLOUDY);
    }
}
