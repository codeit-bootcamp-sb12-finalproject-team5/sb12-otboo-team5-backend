package com.codeit.otboo.domain.feed.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class FeedException extends BusinessException {

    public FeedException(ErrorCode errorCode) {
        super(errorCode);
    }

}
