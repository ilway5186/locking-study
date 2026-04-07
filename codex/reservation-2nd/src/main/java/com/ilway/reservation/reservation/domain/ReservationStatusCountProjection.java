package com.ilway.reservation.reservation.domain;

public interface ReservationStatusCountProjection {

  SeatReservationStatus getStatus();

  Long getCount();
}
