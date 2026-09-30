package com.codeit.otboo.domain.outfit.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class OutfitException extends BusinessException {

    public OutfitException(ErrorCode errorCode) {
        super(errorCode);
    }
}
