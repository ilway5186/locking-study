package com.ilway.reservationsystem.reservation.domain.repository;

import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;

public interface FailureReasonCountProjection {

  ReservationFailureReason getFailureReason();

  Long getCount();
}
