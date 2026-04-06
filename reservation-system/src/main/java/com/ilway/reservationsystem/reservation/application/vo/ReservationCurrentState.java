package com.ilway.reservationsystem.reservation.application.vo;

import com.ilway.reservationsystem.reservation.domain.SeatAvailabilityStatus;

import java.time.Instant;

public record ReservationCurrentState(
  SeatAvailabilityStatus status,
  Long reservationId,
  Instant holdExpiresAt
) {

  public static ReservationCurrentState available() {
    return new ReservationCurrentState(SeatAvailabilityStatus.AVAILABLE, null, null);
  }

}
