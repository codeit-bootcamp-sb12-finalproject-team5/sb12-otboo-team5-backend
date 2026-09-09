package com.codeit.otboo.api.clothes.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class ClothesException extends BusinessException {

    public ClothesException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

}
