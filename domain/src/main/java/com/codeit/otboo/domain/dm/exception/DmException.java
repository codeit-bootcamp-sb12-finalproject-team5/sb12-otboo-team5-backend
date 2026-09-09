package com.codeit.otboo.domain.dm.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class DmException extends BusinessException {

    public DmException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static DmException selfNotAllowed() {
        return new DmException(ErrorCode.DM_SELF_NOT_ALLOWED);
    }

    public static DmException invalidCursor() {
        return new DmException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
