package com.ilway.reservation.reservation.api.dto;

import jakarta.validation.constraints.NotNull;

public record HoldSeatRequest(
    @NotNull Long showId,
    @NotNull Long seatId,
    @NotNull Long userId
) {
}
