package com.ilway.reservation.common.api;

import java.time.Instant;

public record ErrorResponse(
    String code,
    String message,
    Instant timestamp
) {
}
