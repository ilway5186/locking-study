package com.ilway.reservationsystem.show.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
