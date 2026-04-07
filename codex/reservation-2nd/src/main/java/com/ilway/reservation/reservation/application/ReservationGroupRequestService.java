package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationGroupRequest;
import com.ilway.reservation.reservation.domain.ReservationGroupRequestRepository;
import com.ilway.reservation.reservation.domain.ReservationRequestAction;
import com.ilway.reservation.reservation.domain.ReservationRequestStatus;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationGroupRequestService {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final ReservationGroupRequestRepository requestRepository;
  private final ReservationGroupRequestRegistrationWriter registrationWriter;
  private final Duration replayWaitTimeout;
  private final Duration replayPollInterval;

  public ReservationGroupRequestService(
      ReservationGroupRequestRepository requestRepository,
      ReservationGroupRequestRegistrationWriter registrationWriter,
      @Value("${reservation.request.replay-wait-timeout:PT2S}") Duration replayWaitTimeout,
      @Value("${reservation.request.replay-poll-interval:PT0.02S}") Duration replayPollInterval
  ) {
    this.requestRepository = requestRepository;
    this.registrationWriter = registrationWriter;
    this.replayWaitTimeout = replayWaitTimeout;
    this.replayPollInterval = replayPollInterval;
  }

  public ReservationGroupRequestRegistrationResult registerHoldRequest(
      String idempotencyKey,
      Long showId,
      Long userId,
      String normalizedSeatSelectionKey
  ) {
    String resolvedIdempotencyKey = resolveIdempotencyKey(idempotencyKey);
    return requestRepository.findByActionAndUserIdAndIdempotencyKey(ReservationRequestAction.HOLD, userId, resolvedIdempotencyKey)
        .map(existing -> reuseExisting(showId, userId, normalizedSeatSelectionKey, existing))
        .orElseGet(() -> createNewRequest(resolvedIdempotencyKey, showId, userId, normalizedSeatSelectionKey));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markHoldSucceeded(Long requestId, Long reservationGroupId) {
    getRequest(requestId).markSucceeded(reservationGroupId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markHoldFailed(Long requestId, ReservationFailureReason failureReason) {
    getRequest(requestId).markFailed(failureReason);
  }

  public ReservationGroupRequest awaitCompletedRequest(Long requestId) {
    long timeoutNanos = replayWaitTimeout.toNanos();
    long deadline = System.nanoTime() + timeoutNanos;

    while (System.nanoTime() <= deadline) {
      ReservationGroupRequest request = getRequest(requestId);
      if (request.getRequestStatus() != ReservationRequestStatus.IN_PROGRESS) {
        request.increaseReusedCount();
        return requestRepository.save(request);
      }
      LockSupport.parkNanos(Math.max(1L, replayPollInterval.toNanos()));
    }

    throw new ReservationException(ReservationFailureReason.DUPLICATE_REQUEST_IN_PROGRESS);
  }

  @Transactional(readOnly = true)
  public ReservationGroupRequest getRequest(Long requestId) {
    return requestRepository.findById(requestId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.INVALID_REQUEST));
  }

  private ReservationGroupRequestRegistrationResult reuseExisting(
      Long showId,
      Long userId,
      String normalizedSeatSelectionKey,
      ReservationGroupRequest existing
  ) {
    if (!existing.matchesPayload(showId, userId, normalizedSeatSelectionKey)) {
      throw new ReservationException(ReservationFailureReason.IDEMPOTENCY_PAYLOAD_MISMATCH);
    }

    if (existing.getRequestStatus() == ReservationRequestStatus.IN_PROGRESS) {
      return ReservationGroupRequestRegistrationResult.await(existing);
    }

    existing.increaseReusedCount();
    requestRepository.save(existing);
    if (existing.getRequestStatus() == ReservationRequestStatus.SUCCEEDED) {
      return ReservationGroupRequestRegistrationResult.successReplay(existing);
    }
    return ReservationGroupRequestRegistrationResult.failureReplay(existing);
  }

  private ReservationGroupRequestRegistrationResult createNewRequest(
      String idempotencyKey,
      Long showId,
      Long userId,
      String normalizedSeatSelectionKey
  ) {
    try {
      ReservationGroupRequest request = registrationWriter.createInProgress(
          idempotencyKey,
          showId,
          userId,
          normalizedSeatSelectionKey
      );
      return ReservationGroupRequestRegistrationResult.newRequest(request.getId());
    } catch (RuntimeException exception) {
      return requestRepository.findByActionAndUserIdAndIdempotencyKey(ReservationRequestAction.HOLD, userId, idempotencyKey)
          .map(existing -> reuseExisting(showId, userId, normalizedSeatSelectionKey, existing))
          .orElseThrow(() -> exception);
    }
  }

  private String resolveIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null) {
      throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
    }

    String normalized = idempotencyKey.trim();
    if (normalized.isEmpty() || normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
    }
    return normalized;
  }

}
