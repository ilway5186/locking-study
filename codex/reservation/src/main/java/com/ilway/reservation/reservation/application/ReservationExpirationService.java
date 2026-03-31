package com.ilway.reservation.reservation.application;

import com.ilway.reservation.reservation.domain.SeatReservationRepository;
import com.ilway.reservation.reservation.domain.SeatReservationStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReservationExpirationService {

  private final SeatReservationRepository reservationRepository;
  private final SeatReservationTxService txService;
  private final Clock clock;

  public ReservationExpirationService(
      SeatReservationRepository reservationRepository,
      SeatReservationTxService txService,
      Clock clock
  ) {
    this.reservationRepository = reservationRepository;
    this.txService = txService;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${reservation.hold.expiration-scan-millis:30000}")
  public void expireOverdueHolds() {
    // Scheduler is cleanup only; correctness still depends on lazy expiration inside hold/confirm flows.
    Instant now = Instant.now(clock);
    reservationRepository.findByStatusAndHoldExpiresAtLessThanEqual(SeatReservationStatus.HOLD, now)
        .forEach(reservation -> txService.expireReservationIfNeeded(reservation.getId()));
  }
}
