package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.SeatReservationRequest;

public record HoldRequestRegistrationResult(
    Long requestId,
    SeatReservationRequest existingRequest,
    boolean replay
) {

  public static HoldRequestRegistrationResult newRequest(Long requestId) {
    return new HoldRequestRegistrationResult(requestId, null, false);
  }

  public static HoldRequestRegistrationResult replay(SeatReservationRequest request) {
    return new HoldRequestRegistrationResult(request.getId(), request, true);
  }
}
