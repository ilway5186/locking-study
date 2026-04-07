package com.ilway.reservation.reservation.api.dto;

public record HoldReservationGroupResponse(
    ReservationGroupResponse reservationGroup,
    boolean reused
) {
}
