package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.ReservationGroupRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReservationExpirationService {

  private final ReservationGroupRepository reservationGroupRepository;
  private final ReservationGroupTxService txService;
  private final Clock clock;

  public ReservationExpirationService(
      ReservationGroupRepository reservationGroupRepository,
      ReservationGroupTxService txService,
      Clock clock
  ) {
    this.reservationGroupRepository = reservationGroupRepository;
    this.txService = txService;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${reservation.hold.expiration-scan-millis:30000}")
  public void expireOverdueReservationGroups() {
    // Scheduler is cleanup only; correctness still depends on lazy expiration inside hold/confirm flows.
    Instant now = Instant.now(clock);
    reservationGroupRepository.findByStatusAndHoldExpiresAtLessThanEqual(ReservationGroupStatus.HOLD, now)
        .forEach(group -> txService.expireReservationGroupIfNeeded(group.getId()));
    reservationGroupRepository.findByStatusAndHoldExpiresAtLessThanEqual(ReservationGroupStatus.PAYMENT_PENDING, now)
        .forEach(group -> txService.expireReservationGroupIfNeeded(group.getId()));
  }
}
