package com.codeit.otboo.domain.clothes.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class ClothesException extends BusinessException {

    public ClothesException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ClothesException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

}
