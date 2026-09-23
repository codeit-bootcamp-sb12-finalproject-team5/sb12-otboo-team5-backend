package com.codeit.otboo.domain.recommendation.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class RecommendationException extends BusinessException {
    public RecommendationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RecommendationException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
