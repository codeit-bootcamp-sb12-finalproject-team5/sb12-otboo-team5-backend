package com.codeit.otboo.support.weather.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KmaTimeCalculatorTest {
    @ParameterizedTest
    @CsvSource({
        "2026-09-08T14:14:59+09:00, 2026-09-08T13:00:00+09:00",
        "2026-09-08T14:15:00+09:00, 2026-09-08T14:00:00+09:00",
        "2026-01-01T00:10:00+09:00, 2025-12-31T23:00:00+09:00"
    })
    void observationUsesFifteenMinuteBuffer(String now, String expected) {
        assertThat(KmaTimeCalculator.observationBase(OffsetDateTime.parse(now)))
            .isEqualTo(OffsetDateTime.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-08T02:14:59+09:00, 2026-09-07T23:00:00+09:00",
        "2026-09-08T02:15:00+09:00, 2026-09-08T02:00:00+09:00",
        "2026-09-08T05:15:00+09:00, 2026-09-08T05:00:00+09:00",
        "2026-01-01T00:10:00+09:00, 2025-12-31T23:00:00+09:00",
        "2026-09-08T17:15:00Z, 2026-09-09T02:00:00+09:00"
    })
    void villageSelectsPublishedBaseAcrossBoundaries(String now, String expected) {
        assertThat(KmaTimeCalculator.villageBase(OffsetDateTime.parse(now)))
            .isEqualTo(OffsetDateTime.parse(expected));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-09-08T17:35:45Z, 2026-09-09T02:00:00+09:00",
        "2026-09-08T14:35:45+09:00, 2026-09-08T14:00:00+09:00"
    })
    void normalizesToKstHour(String time, String expected) {
        var actual = KmaTimeCalculator.normalizeToKstHour(OffsetDateTime.parse(time));
        assertThat(actual).isEqualTo(OffsetDateTime.parse(expected));
        assertThat(actual.getOffset()).isEqualTo(KmaTimeCalculator.KST);
    }
}
