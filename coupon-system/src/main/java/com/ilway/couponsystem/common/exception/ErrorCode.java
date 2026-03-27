package com.ilway.couponsystem.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

import static org.springframework.http.HttpStatus.*;

@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ErrorCode {

  // 범용
  INVALID_REQUEST(BAD_REQUEST, "COMMON-400", "잘못된 요청입니다."),
  INTERNAL_ERROR(INTERNAL_SERVER_ERROR, "COMMON-500", "서버 내부 오류가 발생했습니다."),

  // 쿠퐁 이벤트
  COUPON_EVENT_NOT_FOUND(NOT_FOUND, "COUPON-EVENT-404", "쿠폰 이벤트를 찾을 수 없습니다."),

  // 크폰 발행
  ISSUE_PERIOD_CLOSED(BAD_REQUEST, "COUPON-ISSUE-400", "발급 기간이 아닙니다."),
  SOLD_OUT(CONFLICT, "COUPON-ISSUE-409", "쿠폰 재고가 소진되었습니다."),
  ALREADY_ISSUED(CONFLICT, "COUPON-ISSUE-410", "이미 쿠폰을 발급받았습니다.");

  private final HttpStatus status;
  private final String code;
  private final String message;

}
