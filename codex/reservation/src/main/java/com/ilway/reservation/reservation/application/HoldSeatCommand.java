package com.ilway.reservation.reservation.application;

public record HoldSeatCommand(
    String idempotencyKey,
    Long showId,
    Long seatId,
    Long userId
) {
}
