package com.codeit.otboo.domain.profile.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class ProfileException extends BusinessException {

  public ProfileException(ErrorCode errorCode) {
    super(errorCode);
  }

  public ProfileException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

  public static ProfileException notFound() {
    return new ProfileException(ErrorCode.PROFILE_NOT_FOUND);
  }

  public static ProfileException resourceNotFound() {
    return new ProfileException(ErrorCode.RESOURCE_NOT_FOUND);
  }
}
