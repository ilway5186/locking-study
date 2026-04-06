package com.ilway.reservationsystem.reservation.application.service;

import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

  private final SeatReservationRepository reservationRepo;
  private final SeatReservationTxService txService;
  private final Clock clock;

  @Scheduled(fixedDelayString = "${reservation.hold.expiration-scan-millis:30000}")
  public void expireOverdueHolds() {
    Instant now = Instant.now(clock);
    reservationRepo.findByStatusAndHoldExpiresAtLessThanEqual(SeatReservationStatus.HOLD, now)
      .forEach(reservation -> txService.expireReservationIfNeeded(reservation.getId()));
  }

}
