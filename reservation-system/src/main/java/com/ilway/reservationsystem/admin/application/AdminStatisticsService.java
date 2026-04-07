package com.ilway.reservationsystem.admin.application;

import com.ilway.reservationsystem.admin.api.AdminStatisticsResponse;
import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.application.service.ReservationExpirationService;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.reservation.domain.ReservationRequestStatus;
import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRepository;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRequestRepository;
import com.ilway.reservationsystem.seat.domain.SeatRepository;
import com.ilway.reservationsystem.show.domain.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatisticsService {

  private final ShowRepository showRepository;
  private final SeatRepository seatRepository;
  private final SeatReservationRepository reservationRepository;
  private final SeatReservationRequestRepository requestRepository;
  private final ReservationExpirationService expirationService;

  @Transactional
  public AdminStatisticsResponse getShowStatistics(Long showId) {
    if (!showRepository.existsById(showId)) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }

    expirationService.expireOverdueHolds();

    Map<SeatReservationStatus, Long> statusCounts = new EnumMap<>(SeatReservationStatus.class);
    reservationRepository.countByShowIdGroupByStatus(showId)
      .forEach(projection -> statusCounts.put(projection.getStatus(), projection.getCount()));

    Map<String, Long> failureReasonCounts = new LinkedHashMap<>();
    requestRepository.countFailureReasonsByShowId(showId)
      .forEach(projection -> failureReasonCounts.put(String.valueOf(projection.getFailureReason()), projection.getCount()));

    return new AdminStatisticsResponse(
      seatRepository.findByShowIdOrderBySeatNumberAsc(showId).size(),
      statusCounts.getOrDefault(SeatReservationStatus.HOLD, 0L),
      statusCounts.getOrDefault(SeatReservationStatus.RESERVED, 0L),
      statusCounts.getOrDefault(SeatReservationStatus.EXPIRED, 0L),
      statusCounts.getOrDefault(SeatReservationStatus.CANCELLED, 0L),
      requestRepository.countByShowId(showId),
      requestRepository.countByShowIdAndRequestStatus(showId, ReservationRequestStatus.FAILED),
      requestRepository.sumReusedCountByShowId(showId),
      failureReasonCounts
    );
  }
  
}
