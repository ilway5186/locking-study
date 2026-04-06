package com.ilway.reservationsystem.reservation.application;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.api.dto.HoldSeatResponse;
import com.ilway.reservationsystem.reservation.api.dto.MyReservationResponse;
import com.ilway.reservationsystem.reservation.api.dto.ReservationResponse;
import com.ilway.reservationsystem.reservation.application.service.ReservationExpirationService;
import com.ilway.reservationsystem.reservation.application.service.SeatReservationRequestService;
import com.ilway.reservationsystem.reservation.application.service.SeatReservationTxService;
import com.ilway.reservationsystem.reservation.application.vo.HoldRequestRegistrationResult;
import com.ilway.reservationsystem.reservation.application.vo.HoldSeatCommand;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.reservation.domain.ReservationRequestStatus;
import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRepository;
import com.ilway.reservationsystem.seat.domain.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatReservationFacade {

  private final SeatReservationRequestService requestService;
  private final SeatReservationTxService txService;
  private final ReservationExpirationService expirationService;
  private final SeatReservationRepository reservationRepo;
  private final SeatRepository seatRepo;

  public HoldSeatResponse hold(HoldSeatCommand command) {
    HoldRequestRegistrationResult registration = requestService.registerHoldRequest(command.idempotencyKey(), command.showId(), command.seatId(), command.userId());
    if (registration.replay()) {
      return replay(registration);
    }

    try {
      ReservationResponse reservation = txService.processHold(command);
      requestService.markHoldSucceeded(registration.requestId(), reservation.reservationId());
      return new HoldSeatResponse(reservation, false);
    } catch (ReservationException e) {
      requestService.markHoldFailed(registration.requestId(), e.getReason());
      throw e;
    } catch (Exception e) {
      requestService.markHoldFailed(registration.requestId(), ReservationFailureReason.INTERNAL_ERROR);
      throw e;
    }
  }

  public ReservationResponse confirm(Long reservationId, Long userId) {
    return txService.confirm(reservationId, userId);
  }

  public ReservationResponse cancel(Long reservationId, Long userId) {
    return txService.cancel(reservationId, userId);
  }

  public List<MyReservationResponse> getMyReservations(Long userId) {
    expirationService.expireOverdueHolds();
    Map<Long, String> seatNumberBySeatId = seatRepo.findAll().stream()
      .collect(Collectors.toMap(seat -> seat.getId(), seat -> seat.getSeatNumber()));
    return txService.getMyReservations(userId, seatNumberBySeatId);
  }

  private HoldSeatResponse replay(HoldRequestRegistrationResult registration) {
    if (registration.existingRequest().getRequestStatus() == ReservationRequestStatus.FAILED) {
      throw new ReservationException(registration.existingRequest().getFailureReason());
    }

    SeatReservation reservation = reservationRepo.findById(registration.existingRequest().getReservationId())
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));

    return new HoldSeatResponse(ReservationResponse.from(reservation), true);
  }

}
