package com.ilway.reservation.reservation.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record HoldReservationGroupRequest(
    @NotNull Long userId,
    @NotEmpty List<Long> seatIds
) {
}
