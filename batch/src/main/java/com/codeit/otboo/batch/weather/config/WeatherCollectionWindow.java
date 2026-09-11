package com.codeit.otboo.batch.weather.config;

import com.codeit.otboo.support.weather.util.KmaTimeCalculator;
import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class WeatherCollectionWindow {

    private static final int OBSERVATION_CONFIRM_MINUTE = 40;
    public static final int OBSERVATION_SLOT_COUNT = 8;
    private static final int FORECAST_START_DAY_OFFSET = 1;
    private static final int FORECAST_END_DAY_OFFSET = 6;

    private WeatherCollectionWindow() {}

    public static OffsetDateTime collectionAt(String value) {
        if (value == null || value.isBlank()) {
            throw new BatchException(ErrorCode.INVALID_BATCH_COLLECTION_TIME);
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(KmaTimeCalculator.KST);
        } catch (DateTimeParseException exception) {
            throw new BatchException(ErrorCode.INVALID_BATCH_COLLECTION_TIME, exception);
        }
    }

    public static OffsetDateTime observationAnchor(OffsetDateTime now) {
        OffsetDateTime kst = now.withOffsetSameInstant(KmaTimeCalculator.KST);
        OffsetDateTime confirmedHour = kst.getMinute() >= OBSERVATION_CONFIRM_MINUTE
                ? kst.truncatedTo(ChronoUnit.HOURS)
                : kst.truncatedTo(ChronoUnit.HOURS).minusHours(1);
        return confirmedHour.minusHours(confirmedHour.getHour() % 3);
    }

    public static List<OffsetDateTime> observationSlots(OffsetDateTime now) {
        OffsetDateTime anchor = observationAnchor(now);
        List<OffsetDateTime> slots = new ArrayList<>();
        for (int i = 0; i < OBSERVATION_SLOT_COUNT; i++) {
            slots.add(anchor.minusHours(3L * i));
        }
        return List.copyOf(slots);
    }

    public static OffsetDateTime forecastRangeStart(LocalDate collectionDate) {
        return collectionDate.plusDays(FORECAST_START_DAY_OFFSET).atStartOfDay().atOffset(KmaTimeCalculator.KST);
    }

    public static OffsetDateTime forecastRangeEnd(LocalDate collectionDate) {
        return collectionDate.plusDays(FORECAST_END_DAY_OFFSET).atStartOfDay().atOffset(KmaTimeCalculator.KST);
    }
}
