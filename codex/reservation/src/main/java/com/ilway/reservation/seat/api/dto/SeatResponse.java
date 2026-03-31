package com.ilway.reservation.seat.api.dto;

import com.ilway.reservation.reservation.domain.SeatAvailabilityStatus;
import java.time.Instant;

public record SeatResponse(
    Long seatId,
    String seatNumber,
    SeatAvailabilityStatus status,
    Instant holdExpiresAt
) {
}
