package com.ilway.reservationsystem.seat.api.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSeatsRequest(
  @Nullable Long showId,
  @NotEmpty List<String> seatNumbers
) {
}
