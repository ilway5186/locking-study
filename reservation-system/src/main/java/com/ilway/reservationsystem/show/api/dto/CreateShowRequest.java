package com.ilway.reservationsystem.show.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateShowRequest(
  @NotBlank String name,
  @NotNull Instant startAt,
  @NotNull Instant endAt,
  @NotNull Instant bookingOpenAt,
  @NotNull Instant bookingCloseAt
) {
}
