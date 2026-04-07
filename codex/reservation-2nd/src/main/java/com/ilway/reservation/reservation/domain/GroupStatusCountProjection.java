package com.ilway.reservation.reservation.domain;

public interface GroupStatusCountProjection {

  ReservationGroupStatus getStatus();

  Long getCount();
}
