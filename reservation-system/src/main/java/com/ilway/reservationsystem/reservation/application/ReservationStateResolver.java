package com.ilway.reservationsystem.reservation.application;

import com.ilway.reservationsystem.reservation.application.vo.ReservationCurrentState;
import com.ilway.reservationsystem.reservation.domain.ReservationRequestStatus;
import com.ilway.reservationsystem.reservation.domain.SeatAvailabilityStatus;
import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

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
      return new ReservationCurrentState(SeatAvailabilityStatus.HOLD, reservation.getId(),reservation.getHoldExpiresAt());
    }

    return new ReservationCurrentState(SeatAvailabilityStatus.RESERVED, reservation.getId(), null);
  }

}
