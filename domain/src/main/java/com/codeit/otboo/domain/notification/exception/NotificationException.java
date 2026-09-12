package com.codeit.otboo.domain.notification.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class NotificationException extends BusinessException {
    public NotificationException(ErrorCode code) {
        super(code);
    }
}
