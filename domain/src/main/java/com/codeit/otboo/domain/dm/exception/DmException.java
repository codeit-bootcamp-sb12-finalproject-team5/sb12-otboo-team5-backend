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

    public static DmException roomNotFound() {
        return new DmException(ErrorCode.ROOM_NOT_FOUND);
    }

    public static DmException invalidMessage() {
        return new DmException(ErrorCode.INVALID_MESSAGE);
    }

    public static DmException messageNotFound() {
        return new DmException(ErrorCode.DM_MESSAGE_NOT_FOUND);
    }

    public static DmException invalidMessageId() {
        return new DmException(ErrorCode.INVALID_INPUT_VALUE);
    }

    public static DmException forbidden() {
        return new DmException(ErrorCode.FORBIDDEN);
    }
}
