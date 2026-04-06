package com.ilway.reservationsystem.common.api;

import java.time.Instant;

public record ErrorResponse(
  String code,
  String message,
  Instant timestamp
) {
}
