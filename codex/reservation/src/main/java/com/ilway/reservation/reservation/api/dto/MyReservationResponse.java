package com.ilway.reservation.reservation.api.dto;

import com.ilway.reservation.reservation.domain.SeatReservationStatus;
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
