package com.ilway.reservationsystem.reservation.api.dto;

import jakarta.validation.constraints.NotNull;

public record CancelReservationRequest(
  @NotNull Long userId
) {
}
