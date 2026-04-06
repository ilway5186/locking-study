package com.ilway.reservation.admin.application;

import com.ilway.reservation.admin.api.dto.AdminStatisticsResponse;
import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.application.ReservationExpirationService;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationRequestStatus;
import com.ilway.reservation.reservation.domain.SeatReservationRequestRepository;
import com.ilway.reservation.reservation.domain.SeatReservationRepository;
import com.ilway.reservation.reservation.domain.SeatReservationStatus;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.ShowRepository;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatisticsService {

  private final ShowRepository showRepository;
  private final SeatRepository seatRepository;
  private final SeatReservationRepository reservationRepository;
  private final SeatReservationRequestRepository requestRepository;
  private final ReservationExpirationService expirationService;

  public AdminStatisticsService(
      ShowRepository showRepository,
      SeatRepository seatRepository,
      SeatReservationRepository reservationRepository,
      SeatReservationRequestRepository requestRepository,
      ReservationExpirationService expirationService
  ) {
    this.showRepository = showRepository;
    this.seatRepository = seatRepository;
    this.reservationRepository = reservationRepository;
    this.requestRepository = requestRepository;
    this.expirationService = expirationService;
  }

  @Transactional
  public AdminStatisticsResponse getShowStatistics(Long showId) {
    if (!showRepository.existsById(showId)) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }

    expirationService.expireOverdueHolds();

    Map<SeatReservationStatus, Long> statusCounts = new EnumMap<>(SeatReservationStatus.class);
    reservationRepository.countByShowIdGroupByStatus(showId).forEach(projection ->
        statusCounts.put(projection.getStatus(), projection.getCount()));

    Map<String, Long> failureReasonCounts = new LinkedHashMap<>();
    requestRepository.countFailureReasonsByShowId(showId).forEach(projection ->
        failureReasonCounts.put(String.valueOf(projection.getFailureReason()), projection.getCount()));

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
