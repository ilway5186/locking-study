package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.SeatAvailabilityStatus;
import java.time.Instant;

public record ReservationCurrentState(
    SeatAvailabilityStatus status,
    Long reservationGroupId,
    Instant holdExpiresAt
) {

  public static ReservationCurrentState available() {
    return new ReservationCurrentState(SeatAvailabilityStatus.AVAILABLE, null, null);
  }
}
