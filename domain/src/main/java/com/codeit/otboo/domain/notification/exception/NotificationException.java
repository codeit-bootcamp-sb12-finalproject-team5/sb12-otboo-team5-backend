package com.codeit.otboo.domain.notification.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class NotificationException extends BusinessException {

    public NotificationException(ErrorCode code) {
        super(code);
    }

    public NotificationException(ErrorCode code, Throwable cause) {
        super(code, cause);
    }
}
