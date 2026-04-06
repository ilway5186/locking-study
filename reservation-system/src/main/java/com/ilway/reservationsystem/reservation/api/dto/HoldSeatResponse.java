package com.ilway.reservationsystem.reservation.api.dto;

public record HoldSeatResponse(
  ReservationResponse reservation,
  boolean reused
) {
}
