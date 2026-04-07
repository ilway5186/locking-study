package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.api.dto.HoldReservationGroupResponse;
import com.ilway.reservation.reservation.api.dto.ReservationGroupResponse;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationGroupRequest;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ReservationGroupFacade {

  private final SeatSelectionNormalizer seatSelectionNormalizer;
  private final ReservationGroupRequestService requestService;
  private final ReservationGroupTxService txService;

  public ReservationGroupFacade(
      SeatSelectionNormalizer seatSelectionNormalizer,
      ReservationGroupRequestService requestService,
      ReservationGroupTxService txService
  ) {
    this.seatSelectionNormalizer = seatSelectionNormalizer;
    this.requestService = requestService;
    this.txService = txService;
  }

  public HoldReservationGroupResponse hold(HoldReservationGroupCommand command) {
    NormalizedSeatSelection selection = seatSelectionNormalizer.normalize(command.seatIds());
    ReservationGroupRequestRegistrationResult registration = requestService.registerHoldRequest(
        command.idempotencyKey(),
        command.showId(),
        command.userId(),
        selection.selectionKey()
    );

    if (registration.isAwaitingCompletion()) {
      return replay(requestService.awaitCompletedRequest(registration.requestId()));
    }
    if (registration.isReplay()) {
      return replay(registration.existingRequest());
    }

    try {
      ReservationGroupResponse reservationGroup = txService.processHold(command.showId(), command.userId(), selection.seatIds());
      requestService.markHoldSucceeded(registration.requestId(), reservationGroup.reservationGroupId());
      return new HoldReservationGroupResponse(reservationGroup, false);
    } catch (ReservationException exception) {
      requestService.markHoldFailed(registration.requestId(), exception.getReason());
      throw exception;
    } catch (Exception exception) {
      requestService.markHoldFailed(registration.requestId(), ReservationFailureReason.INTERNAL_ERROR);
      throw exception;
    }
  }

  public ReservationGroupResponse getGroup(Long groupId) {
    return txService.getGroup(groupId);
  }

  public List<ReservationGroupResponse> getUserGroups(Long userId) {
    return txService.getUserGroups(userId);
  }

  public ReservationGroupResponse confirm(Long groupId, Long userId) {
    return txService.confirm(groupId, userId);
  }

  public ReservationGroupResponse cancel(Long groupId, Long userId) {
    return txService.cancel(groupId, userId);
  }

  private HoldReservationGroupResponse replay(ReservationGroupRequest request) {
    if (request.getRequestStatus() == com.ilway.reservation.reservation.domain.ReservationRequestStatus.FAILED) {
      throw new ReservationException(request.getFailureReason());
    }
    return new HoldReservationGroupResponse(txService.getGroup(request.getReservationGroupId()), true);
  }
}
