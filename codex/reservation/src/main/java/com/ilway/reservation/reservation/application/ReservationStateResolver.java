package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.SeatAvailabilityStatus;
import com.ilway.reservation.reservation.domain.SeatReservation;
import com.ilway.reservation.reservation.domain.SeatReservationStatus;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ReservationStateResolver {

  public ReservationCurrentState resolve(SeatReservation reservation, Instant now) {
    if (reservation == null) {
      return ReservationCurrentState.available();
    }
    if (reservation.getStatus() == SeatReservationStatus.HOLD && reservation.isExpiredAt(now)) {
      return ReservationCurrentState.available();
    }
    if (reservation.getStatus() == SeatReservationStatus.CANCELLED || reservation.getStatus() == SeatReservationStatus.EXPIRED) {
      return ReservationCurrentState.available();
    }
    if (reservation.getStatus() == SeatReservationStatus.HOLD) {
      return new ReservationCurrentState(SeatAvailabilityStatus.HOLD, reservation.getId(), reservation.getHoldExpiresAt());
    }
    return new ReservationCurrentState(SeatAvailabilityStatus.RESERVED, reservation.getId(), null);
  }
}
