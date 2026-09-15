package com.codeit.otboo.domain.profile.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class ProfileException extends BusinessException {

    public ProfileException(ErrorCode errorCode) {
        super(errorCode);
    }

    public static ProfileException notFound() {
        return new ProfileException(ErrorCode.PROFILE_NOT_FOUND);
    }

    public static ProfileException invalidTemperatureSensitivity() {
        return new ProfileException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
