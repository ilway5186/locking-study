package com.ilway.reservation.common.exception;

import com.ilway.reservation.reservation.domain.ReservationFailureReason;

public class ReservationException extends RuntimeException {

  private final ReservationFailureReason reason;

  public ReservationException(ReservationFailureReason reason) {
    super(reason.getMessage());
    this.reason = reason;
  }

  public ReservationFailureReason getReason() {
    return reason;
  }
}
