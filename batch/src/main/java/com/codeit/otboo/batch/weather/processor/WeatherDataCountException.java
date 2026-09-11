package com.codeit.otboo.batch.weather.processor;

import com.codeit.otboo.batch.common.exception.BatchException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import java.util.UUID;

public class WeatherDataCountException extends BatchException {
    public WeatherDataCountException(UUID gridId, int nx, int ny) {
        super(ErrorCode.BATCH_WEATHER_DATA_UNAVAILABLE);
        addDetail("gridId", gridId);
        addDetail("nx", nx);
        addDetail("ny", ny);
    }
}
