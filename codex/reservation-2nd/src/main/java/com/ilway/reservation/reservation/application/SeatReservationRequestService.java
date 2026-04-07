package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationRequestAction;
import com.ilway.reservation.reservation.domain.ReservationRequestStatus;
import com.ilway.reservation.reservation.domain.SeatReservationRequest;
import com.ilway.reservation.reservation.domain.SeatReservationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatReservationRequestService {

  private final SeatReservationRequestRepository requestRepository;

  public SeatReservationRequestService(SeatReservationRequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public HoldRequestRegistrationResult registerHoldRequest(String idempotencyKey, Long showId, Long seatId, Long userId) {
    return requestRepository.findByActionAndUserIdAndIdempotencyKey(ReservationRequestAction.HOLD, userId, idempotencyKey)
        .map(existing -> reuseExisting(showId, seatId, userId, existing))
        .orElseGet(() -> createNewRequest(idempotencyKey, showId, seatId, userId));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markHoldSucceeded(Long requestId, Long reservationId) {
    SeatReservationRequest request = getRequest(requestId);
    request.markSucceeded(reservationId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markHoldFailed(Long requestId, ReservationFailureReason failureReason) {
    SeatReservationRequest request = getRequest(requestId);
    request.markFailed(failureReason);
  }

  private HoldRequestRegistrationResult reuseExisting(
      Long showId,
      Long seatId,
      Long userId,
      SeatReservationRequest existing
  ) {
    if (!existing.matchesPayload(showId, seatId, userId)) {
      throw new ReservationException(ReservationFailureReason.IDEMPOTENCY_PAYLOAD_MISMATCH);
    }
    if (existing.getRequestStatus() == ReservationRequestStatus.IN_PROGRESS) {
      throw new ReservationException(ReservationFailureReason.DUPLICATE_REQUEST_IN_PROGRESS);
    }
    existing.increaseReusedCount();
    return HoldRequestRegistrationResult.replay(existing);
  }

  private HoldRequestRegistrationResult createNewRequest(String idempotencyKey, Long showId, Long seatId, Long userId) {
    try {
      SeatReservationRequest request = requestRepository.save(SeatReservationRequest.hold(idempotencyKey, showId, seatId, userId));
      return HoldRequestRegistrationResult.newRequest(request.getId());
    } catch (DataIntegrityViolationException exception) {
      return requestRepository.findByActionAndUserIdAndIdempotencyKey(ReservationRequestAction.HOLD, userId, idempotencyKey)
          .map(existing -> reuseExisting(showId, seatId, userId, existing))
          .orElseThrow(() -> exception);
    }
  }

  private SeatReservationRequest getRequest(Long requestId) {
    return requestRepository.findById(requestId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.INVALID_REQUEST));
  }
}
