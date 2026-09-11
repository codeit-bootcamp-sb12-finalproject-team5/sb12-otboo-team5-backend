package com.codeit.otboo.support.weather.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

public final class KmaTimeCalculator {
    public static final ZoneOffset KST = ZoneOffset.ofHours(9);
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmm");
    public static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
        .appendPattern("yyyyMMddHHmm")
        .parseDefaulting(ChronoField.OFFSET_SECONDS, KST.getTotalSeconds())
        .toFormatter();

    private static final int[] VILLAGE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};

    private KmaTimeCalculator() {}

    // 15분의 공개 지연을 고려한 최신 초단기실황 기준 시각
    public static OffsetDateTime currentObservationBase() {
        return observationBase(OffsetDateTime.now(KST));
    }

    static OffsetDateTime observationBase(OffsetDateTime now) {
        return normalizeToKstHour(now.minusMinutes(15));
    }

    // 15분의 공개 지연을 고려한 최신 단기예보 발표 시각
    public static OffsetDateTime currentVillageBase() {
        return villageBase(OffsetDateTime.now(KST));
    }

    public static OffsetDateTime villageBase(OffsetDateTime now) {
        OffsetDateTime target = now.withOffsetSameInstant(KST).minusMinutes(15);
        for (int i = VILLAGE_HOURS.length - 1; i >= 0; i--) {
            if (target.getHour() >= VILLAGE_HOURS[i]) {
                return target.withHour(VILLAGE_HOURS[i]).truncatedTo(ChronoUnit.HOURS);
            }
        }
        return target.minusDays(1).withHour(23).truncatedTo(ChronoUnit.HOURS);
    }

    // 동일한 순간을 한국 시간으로 변환한 뒤 정각으로 정규화
    public static OffsetDateTime normalizeToKstHour(OffsetDateTime time) {
        return time.withOffsetSameInstant(KST).truncatedTo(ChronoUnit.HOURS);
    }
}
