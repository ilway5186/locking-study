package com.ilway.reservationsystem.reservation.domain.repository;

import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;

public interface ReservationStatusCountProjection {

  SeatReservationStatus getStatus();

  Long getCount();
}
