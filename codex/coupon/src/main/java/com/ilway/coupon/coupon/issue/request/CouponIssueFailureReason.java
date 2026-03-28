package com.ilway.coupon.coupon.issue.request;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;

public enum CouponIssueFailureReason {
  EVENT_NOT_FOUND(ErrorCode.COUPON_EVENT_NOT_FOUND),
  NOT_IN_ISSUE_PERIOD(ErrorCode.ISSUE_PERIOD_CLOSED),
  ALREADY_ISSUED(ErrorCode.ALREADY_ISSUED),
  SOLD_OUT(ErrorCode.SOLD_OUT),
  CONFLICT_RETRY_EXCEEDED(ErrorCode.CONFLICT_RETRY_EXCEEDED),
  INTERNAL_ERROR(ErrorCode.INTERNAL_ERROR);

  private final ErrorCode errorCode;

  CouponIssueFailureReason(ErrorCode errorCode) {
    this.errorCode = errorCode;
  }

  public static CouponIssueFailureReason from(ErrorCode errorCode) {
    return switch (errorCode) {
      case COUPON_EVENT_NOT_FOUND -> EVENT_NOT_FOUND;
      case ISSUE_PERIOD_CLOSED -> NOT_IN_ISSUE_PERIOD;
      case ALREADY_ISSUED -> ALREADY_ISSUED;
      case SOLD_OUT -> SOLD_OUT;
      case CONFLICT_RETRY_EXCEEDED -> CONFLICT_RETRY_EXCEEDED;
      default -> INTERNAL_ERROR;
    };
  }

  public BusinessException toBusinessException() {
    return new BusinessException(errorCode);
  }
}
