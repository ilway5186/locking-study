package com.ilway.reservationsystem.seat.api.dto;

import com.ilway.reservationsystem.reservation.domain.SeatAvailabilityStatus;

import java.time.Instant;

public record SeatResponse(
  Long seatId,
  String seatNumber,
  SeatAvailabilityStatus status,
  Instant holdExpiresAt
) {
}
