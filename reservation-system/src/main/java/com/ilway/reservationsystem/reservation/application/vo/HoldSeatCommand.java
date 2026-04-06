package com.ilway.reservationsystem.reservation.application.vo;

public record HoldSeatCommand(
  String idempotencyKey,
  Long showId,
  Long seatId,
  Long userId
) {
}
