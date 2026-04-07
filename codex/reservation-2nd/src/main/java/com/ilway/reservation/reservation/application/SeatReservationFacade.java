package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.api.dto.HoldSeatResponse;
import com.ilway.reservation.reservation.api.dto.MyReservationResponse;
import com.ilway.reservation.reservation.api.dto.ReservationResponse;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationRequestStatus;
import com.ilway.reservation.reservation.domain.SeatReservation;
import com.ilway.reservation.reservation.domain.SeatReservationRepository;
import com.ilway.reservation.seat.domain.SeatRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SeatReservationFacade {

  private final SeatReservationRequestService requestService;
  private final SeatReservationTxService txService;
  private final SeatReservationRepository reservationRepository;
  private final ReservationExpirationService expirationService;
  private final SeatRepository seatRepository;

  public SeatReservationFacade(
      SeatReservationRequestService requestService,
      SeatReservationTxService txService,
      SeatReservationRepository reservationRepository,
      ReservationExpirationService expirationService,
      SeatRepository seatRepository
  ) {
    this.requestService = requestService;
    this.txService = txService;
    this.reservationRepository = reservationRepository;
    this.expirationService = expirationService;
    this.seatRepository = seatRepository;
  }

  public HoldSeatResponse hold(HoldSeatCommand command) {
    HoldRequestRegistrationResult registration = requestService.registerHoldRequest(
        command.idempotencyKey(),
        command.showId(),
        command.seatId(),
        command.userId()
    );

    if (registration.replay()) {
      return replay(registration);
    }

    try {
      ReservationResponse reservation = txService.processHold(command);
      requestService.markHoldSucceeded(registration.requestId(), reservation.reservationId());
      return new HoldSeatResponse(reservation, false);
    } catch (ReservationException exception) {
      requestService.markHoldFailed(registration.requestId(), exception.getReason());
      throw exception;
    } catch (Exception exception) {
      requestService.markHoldFailed(registration.requestId(), ReservationFailureReason.INTERNAL_ERROR);
      throw exception;
    }
  }

  public ReservationResponse confirm(Long reservationId, Long userId) {
    return txService.confirm(reservationId, userId);
  }

  public ReservationResponse cancel(Long reservationId, Long userId) {
    return txService.cancel(reservationId, userId);
  }

  public List<MyReservationResponse> getMyReservations(Long userId) {
    expirationService.expireOverdueReservationGroups();
    Map<Long, String> seatNumberBySeatId = seatRepository.findAll().stream()
        .collect(Collectors.toMap(seat -> seat.getId(), seat -> seat.getSeatNumber()));
    return txService.getMyReservations(userId, seatNumberBySeatId);
  }

  private HoldSeatResponse replay(HoldRequestRegistrationResult registration) {
    if (registration.existingRequest().getRequestStatus() == ReservationRequestStatus.FAILED) {
      throw new ReservationException(registration.existingRequest().getFailureReason());
    }
    SeatReservation reservation = reservationRepository.findById(registration.existingRequest().getReservationId())
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));
    return new HoldSeatResponse(txService.toReservationResponse(reservation), true);
  }
}
