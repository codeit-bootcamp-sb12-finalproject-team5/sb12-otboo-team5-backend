package com.codeit.otboo.domain.user.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class UserException extends BusinessException {

    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public static UserException notFound() {
        return new UserException(ErrorCode.USER_NOT_FOUND);
    }

    public static UserException duplicateEmail() {
        return new UserException(ErrorCode.DUPLICATE_EMAIL);
    }

    public static UserException invalidCredentials() {
        return new UserException(ErrorCode.INVALID_CREDENTIALS);
    }

    public static UserException accountLocked() {
        return new UserException(ErrorCode.ACCOUNT_LOCKED);
    }
}
