package com.codeit.otboo.api.common.dto;

import com.codeit.otboo.domain.common.exception.BusinessException;
import java.util.Map;

public record ErrorResponse(
        String exceptionName,
        String message,
        Map<String, Object> details
) {

    public static ErrorResponse of(BusinessException e) {
        return new ErrorResponse(
                e.getClass().getSimpleName(),
                e.getMessage(),
                e.getDetails()
        );
    }

    public static ErrorResponse of(String exceptionName, String message) {
        return new ErrorResponse(exceptionName, message, Map.of());
    }

    public static ErrorResponse of(String exceptionName, String message, Map<String, Object> details) {
        return new ErrorResponse(exceptionName, message, details);
    }
}
