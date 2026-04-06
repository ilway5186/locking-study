package com.ilway.reservationsystem.reservation.api.dto;

import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;

import java.time.Instant;

public record ReservationResponse(
  Long reservationId,
  Long showId,
  Long seatId,
  Long userId,
  SeatReservationStatus status,
  Instant holdExpiresAt,
  Instant confirmedAt,
  Instant cancelledAt,
  Instant expireAt
) {

  public static ReservationResponse from(SeatReservation reservation) {
    return new ReservationResponse(
      reservation.getId(),
      reservation.getShowId(),
      reservation.getSeatId(),
      reservation.getUserId(),
      reservation.getStatus(),
      reservation.getHoldExpiresAt(),
      reservation.getConfirmedAt(),
      reservation.getCancelledAt(),
      reservation.getExpiredAt()
    );
  }

}
