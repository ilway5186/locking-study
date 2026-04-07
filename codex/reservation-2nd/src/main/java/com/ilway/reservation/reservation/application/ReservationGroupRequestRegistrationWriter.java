package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.ReservationGroupRequest;
import com.ilway.reservation.reservation.domain.ReservationGroupRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationGroupRequestRegistrationWriter {

  private final ReservationGroupRequestRepository requestRepository;

  public ReservationGroupRequestRegistrationWriter(ReservationGroupRequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ReservationGroupRequest createInProgress(
      String idempotencyKey,
      Long showId,
      Long userId,
      String normalizedSeatSelectionKey
  ) {
    return requestRepository.saveAndFlush(
        ReservationGroupRequest.hold(idempotencyKey, showId, userId, normalizedSeatSelectionKey)
    );
  }
}
