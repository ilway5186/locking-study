package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.ReservationGroupRequest;

public record ReservationGroupRequestRegistrationResult(
    Long requestId,
    ReservationGroupRequest existingRequest,
    RegistrationType type
) {

  public static ReservationGroupRequestRegistrationResult newRequest(Long requestId) {
    return new ReservationGroupRequestRegistrationResult(requestId, null, RegistrationType.NEW);
  }

  public static ReservationGroupRequestRegistrationResult successReplay(ReservationGroupRequest existingRequest) {
    return new ReservationGroupRequestRegistrationResult(existingRequest.getId(), existingRequest, RegistrationType.SUCCESS_REPLAY);
  }

  public static ReservationGroupRequestRegistrationResult failureReplay(ReservationGroupRequest existingRequest) {
    return new ReservationGroupRequestRegistrationResult(existingRequest.getId(), existingRequest, RegistrationType.FAILURE_REPLAY);
  }

  public static ReservationGroupRequestRegistrationResult await(ReservationGroupRequest existingRequest) {
    return new ReservationGroupRequestRegistrationResult(existingRequest.getId(), existingRequest, RegistrationType.AWAIT_COMPLETION);
  }

  public boolean isNewRequest() {
    return type == RegistrationType.NEW;
  }

  public boolean isReplay() {
    return type == RegistrationType.SUCCESS_REPLAY || type == RegistrationType.FAILURE_REPLAY;
  }

  public boolean isAwaitingCompletion() {
    return type == RegistrationType.AWAIT_COMPLETION;
  }

  public enum RegistrationType {
    NEW,
    SUCCESS_REPLAY,
    FAILURE_REPLAY,
    AWAIT_COMPLETION
  }
}
