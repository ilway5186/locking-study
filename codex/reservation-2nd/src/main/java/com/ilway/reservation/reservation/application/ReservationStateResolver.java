package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.SeatAvailabilityStatus;
import com.ilway.reservation.reservation.domain.ReservationGroup;
import com.ilway.reservation.reservation.domain.ReservationGroupSeat;
import com.ilway.reservation.reservation.domain.ReservationGroupStatus;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ReservationStateResolver {

  public ReservationCurrentState resolve(ReservationGroupSeat reservationGroupSeat, Instant now) {
    if (reservationGroupSeat == null) {
      return ReservationCurrentState.available();
    }
    ReservationGroup group = reservationGroupSeat.getReservationGroup();
    if (group == null) {
      return ReservationCurrentState.available();
    }
    if (group.isExpiredAt(now)) {
      return ReservationCurrentState.available();
    }
    if (group.getStatus() == ReservationGroupStatus.CANCELLED || group.getStatus() == ReservationGroupStatus.EXPIRED) {
      return ReservationCurrentState.available();
    }
    if (group.getStatus() == ReservationGroupStatus.HOLD) {
      return new ReservationCurrentState(SeatAvailabilityStatus.HOLD, group.getId(), group.getHoldExpiresAt());
    }
    if (group.getStatus() == ReservationGroupStatus.PAYMENT_PENDING) {
      return new ReservationCurrentState(SeatAvailabilityStatus.PAYMENT_PENDING, group.getId(), group.getHoldExpiresAt());
    }
    return new ReservationCurrentState(SeatAvailabilityStatus.RESERVED, group.getId(), null);
  }
}
