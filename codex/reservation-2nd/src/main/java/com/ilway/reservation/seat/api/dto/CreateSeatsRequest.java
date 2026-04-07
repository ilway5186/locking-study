package com.ilway.reservation.seat.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateSeatsRequest(
    @NotNull Long showId,
    @NotEmpty List<String> seatNumbers
) {
}
