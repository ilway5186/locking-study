package com.ilway.reservation.reservation.domain;

public interface FailureReasonCountProjection {

  ReservationFailureReason getFailureReason();

  Long getCount();
}
