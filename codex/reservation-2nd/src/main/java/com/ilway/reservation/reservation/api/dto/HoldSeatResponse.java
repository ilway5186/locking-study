package com.ilway.reservation.reservation.api.dto;

public record HoldSeatResponse(
    ReservationResponse reservation,
    boolean reused
) {
}
