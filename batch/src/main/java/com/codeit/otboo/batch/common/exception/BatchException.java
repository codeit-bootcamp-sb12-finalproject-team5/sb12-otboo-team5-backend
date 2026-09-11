package com.codeit.otboo.batch.common.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class BatchException extends BusinessException {
    public BatchException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BatchException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
