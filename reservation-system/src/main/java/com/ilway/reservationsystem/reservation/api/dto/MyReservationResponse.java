package com.ilway.reservationsystem.reservation.api.dto;

import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;

import java.time.Instant;

public record MyReservationResponse(
  Long reservationId,
  Long showId,
  Long seatId,
  Long userId,
  String seatNumber,
  SeatReservationStatus status,
  Instant holdExpiresAt,
  Instant confirmedAt,
  Instant cancelledAt,
  Instant expiredAt,
  Instant createdAt
) {
}
