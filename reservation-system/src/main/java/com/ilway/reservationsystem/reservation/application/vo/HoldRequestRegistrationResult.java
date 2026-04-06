package com.ilway.reservationsystem.reservation.application.vo;

import com.ilway.reservationsystem.reservation.domain.SeatReservationRequest;

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
