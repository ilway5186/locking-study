package com.ilway.reservation.admin.application;

import com.ilway.reservation.admin.api.dto.AdminStatisticsResponse;
import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.application.ReservationExpirationService;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationGroupRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupRequestRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupStatus;
import com.ilway.reservation.reservation.domain.ReservationRequestStatus;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.ShowRepository;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatisticsService {

  private final ShowRepository showRepository;
  private final SeatRepository seatRepository;
  private final ReservationGroupRepository reservationGroupRepository;
  private final ReservationGroupRequestRepository requestRepository;
  private final ReservationExpirationService expirationService;

  public AdminStatisticsService(
      ShowRepository showRepository,
      SeatRepository seatRepository,
      ReservationGroupRepository reservationGroupRepository,
      ReservationGroupRequestRepository requestRepository,
      ReservationExpirationService expirationService
  ) {
    this.showRepository = showRepository;
    this.seatRepository = seatRepository;
    this.reservationGroupRepository = reservationGroupRepository;
    this.requestRepository = requestRepository;
    this.expirationService = expirationService;
  }

  @Transactional
  public AdminStatisticsResponse getShowStatistics(Long showId) {
    if (!showRepository.existsById(showId)) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }

    expirationService.expireOverdueReservationGroups();

    Map<ReservationGroupStatus, Long> statusCounts = new EnumMap<>(ReservationGroupStatus.class);
    reservationGroupRepository.countByShowIdGroupByStatus(showId).forEach(projection ->
        statusCounts.put(projection.getStatus(), projection.getCount()));

    Map<String, Long> failureReasonCounts = new LinkedHashMap<>();
    requestRepository.countFailureReasonsByShowId(showId).forEach(projection ->
        failureReasonCounts.put(String.valueOf(projection.getFailureReason()), projection.getCount()));

    Map<String, Long> groupStatusCounts = new LinkedHashMap<>();
    for (ReservationGroupStatus status : ReservationGroupStatus.values()) {
      groupStatusCounts.put(status.name(), statusCounts.getOrDefault(status, 0L));
    }

    return new AdminStatisticsResponse(
        seatRepository.findByShowIdOrderBySeatNumberAsc(showId).size(),
        requestRepository.countByShowId(showId),
        requestRepository.countByShowIdAndRequestStatus(showId, ReservationRequestStatus.SUCCEEDED),
        requestRepository.countByShowIdAndRequestStatus(showId, ReservationRequestStatus.FAILED),
        requestRepository.sumReusedCountByShowId(showId),
        requestRepository.countByShowIdAndFailureReasonIn(
            showId,
            List.of(
                ReservationFailureReason.SEAT_ALREADY_HELD,
                ReservationFailureReason.SEAT_PAYMENT_PENDING,
                ReservationFailureReason.SEAT_ALREADY_RESERVED
            )
        ),
        groupStatusCounts,
        failureReasonCounts
    );
  }
}
