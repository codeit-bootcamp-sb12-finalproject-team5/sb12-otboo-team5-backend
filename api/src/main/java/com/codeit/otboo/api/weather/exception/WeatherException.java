package com.codeit.otboo.api.weather.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class WeatherException extends BusinessException {

    public WeatherException(ErrorCode errorCode) {
        super(errorCode);
    }
}
