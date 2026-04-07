package com.ilway.reservation.reservation.application;

import java.util.List;

public record HoldReservationGroupCommand(
    String idempotencyKey,
    Long showId,
    Long userId,
    List<Long> seatIds
) {
}
