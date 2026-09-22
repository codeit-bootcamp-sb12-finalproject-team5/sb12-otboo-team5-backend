package com.codeit.otboo.domain.follow.exception;

import com.codeit.otboo.domain.common.exception.BusinessException;
import com.codeit.otboo.domain.common.exception.ErrorCode;

public class FollowException extends BusinessException {

  public FollowException(ErrorCode errorCode) {
    super(errorCode);
  }

  public FollowException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }

  public static FollowException duplicate() {
    return new FollowException(ErrorCode.DUPLICATE_FOLLOW);
  }

  public static FollowException selfFollowNotAllowed() {
    return new FollowException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
  }

}
