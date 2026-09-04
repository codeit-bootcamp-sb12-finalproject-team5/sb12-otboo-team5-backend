package com.codeit.otboo.api.common.exception;

import com.codeit.otboo.api.common.dto.ErrorResponse;
import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("[{}] {} details={}",
                e.getErrorCode().name(), e.getMessage(), e.getDetails());

        return ResponseEntity
                .status(HttpStatus.valueOf(e.getErrorCode().getStatus()))
                .body(ErrorResponse.of(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, Object> details = new HashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("[VALIDATION] {}", details);

        ErrorCode code = ErrorCode.INVALID_INPUT_VALUE;
        return ResponseEntity
                .status(HttpStatus.valueOf(code.getStatus()))
                .body(ErrorResponse.of(
                        e.getClass().getSimpleName(),
                        code.getMessage(),
                        details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("[UNHANDLED] {}", e.getMessage(), e);

        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(HttpStatus.valueOf(code.getStatus()))
                .body(ErrorResponse.of(
                        e.getClass().getSimpleName(),
                        code.getMessage()));
    }
}
