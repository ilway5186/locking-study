package com.ilway.reservation.reservation.api.dto;

import jakarta.validation.constraints.NotNull;

public record CancelReservationGroupRequest(
    @NotNull Long userId
) {
}
