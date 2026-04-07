package com.ilway.reservation.reservation.api.dto;

import com.ilway.reservation.reservation.domain.SeatReservationStatus;
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
    Instant expiredAt
) {
}
