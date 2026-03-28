package com.ilway.coupon.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON-400", "잘못된 요청입니다."),
  COUPON_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON-EVENT-404", "쿠폰 이벤트를 찾을 수 없습니다."),
  ISSUE_PERIOD_CLOSED(HttpStatus.BAD_REQUEST, "COUPON-ISSUE-400", "발급 기간이 아닙니다."),
  SOLD_OUT(HttpStatus.CONFLICT, "COUPON-ISSUE-409", "쿠폰 재고가 소진되었습니다."),
  ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON-ISSUE-410", "이미 쿠폰을 발급받았습니다."),
  DUPLICATE_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "COUPON-ISSUE-411", "같은 idempotency 요청이 아직 처리 중입니다."),
  CONFLICT_RETRY_EXCEEDED(HttpStatus.CONFLICT, "COUPON-ISSUE-412", "동시성 충돌 재시도 한도를 초과했습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류가 발생했습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

  ErrorCode(HttpStatus status, String code, String message) {
    this.status = status;
    this.code = code;
    this.message = message;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
