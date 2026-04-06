package com.ilway.reservationsystem.reservation.domain;

import org.springframework.http.HttpStatus;

public enum ReservationFailureReason {

  SHOW_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
  SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
  BOOKING_NOT_OPEN(HttpStatus.BAD_REQUEST, "예매 오픈 전입니다."),
  BOOKING_CLOSED(HttpStatus.BAD_REQUEST, "예매가 마감되었습니다."),
  SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 다른 요청이 좌석을 HOLD 중입니다."),
  SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "이미 예약이 확정된 좌석입니다."),
  HOLD_EXPIRED(HttpStatus.CONFLICT, "HOLD가 만료되었습니다."),
  RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."),
  FORBIDDEN_RESERVATION_ACCESS(HttpStatus.FORBIDDEN, "다른 사용자의 예약에는 접근할 수 없습니다."),
  DUPLICATE_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "같은 멱등 요청이 아직 처리 중입니다."),
  IDEMPOTENCY_PAYLOAD_MISMATCH(HttpStatus.BAD_REQUEST, "같은 멱등 키를 다른 요청 본문에 재사용할 수 없습니다."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
  INVALID_RESERVATION_STATE(HttpStatus.CONFLICT, "현재 예약 상태에서는 요청을 수행할 수 없습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 오류가 발생했습니다.");

  private final HttpStatus httpStatus;
  private final String message;

  ReservationFailureReason(HttpStatus httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getMessage() {
    return message;
  }
}
