package com.ilway.reservationsystem.reservation.api.dto;

import jakarta.validation.constraints.NotNull;

public record ConfirmReservationRequest(
  @NotNull Long userId
) {
}
