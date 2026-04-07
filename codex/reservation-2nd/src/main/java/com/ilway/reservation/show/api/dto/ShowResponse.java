package com.ilway.reservation.show.api.dto;

import java.time.Instant;

public record ShowResponse(
    Long showId,
    String name,
    Instant startAt,
    Instant endAt,
    Instant bookingOpenAt,
    Instant bookingCloseAt
) {
}
