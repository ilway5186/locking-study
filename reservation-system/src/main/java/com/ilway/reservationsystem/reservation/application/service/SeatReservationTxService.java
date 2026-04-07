package com.ilway.reservationsystem.reservation.application.service;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.api.dto.MyReservationResponse;
import com.ilway.reservationsystem.reservation.api.dto.ReservationResponse;
import com.ilway.reservationsystem.reservation.application.BookingWindowPolicy;
import com.ilway.reservationsystem.reservation.application.vo.HoldSeatCommand;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRepository;
import com.ilway.reservationsystem.seat.domain.Seat;
import com.ilway.reservationsystem.seat.domain.SeatRepository;
import com.ilway.reservationsystem.show.domain.Show;
import com.ilway.reservationsystem.show.domain.ShowRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SeatReservationTxService {

  private final ShowRepository showRepo;
  private final SeatRepository seatRepo;
  private final SeatReservationRepository reservationRepo;
  private final BookingWindowPolicy bookingWindowPolicy;
  private final EntityManager entityManager;
  private final Clock clock;
  private final Duration holdDuration;

  public SeatReservationTxService(
    ShowRepository showRepo,
    SeatRepository seatRepo,
    SeatReservationRepository reservationRepo,
    BookingWindowPolicy bookingWindowPolicy,
    EntityManager entityManager,
    Clock clock,
    @Value("${reservation.hold.duration:PT5M}") Duration holdDuration
  ) {
    this.showRepo = showRepo;
    this.seatRepo = seatRepo;
    this.reservationRepo = reservationRepo;
    this.bookingWindowPolicy = bookingWindowPolicy;
    this.entityManager = entityManager;
    this.clock = clock;
    this.holdDuration = holdDuration;
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationResponse processHold(HoldSeatCommand command) {
    Instant now = Instant.now(clock);
    Show show = showRepo.findById(command.showId())
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND));
    bookingWindowPolicy.validateHoldable(show, now);

    Seat seat = seatRepo.findByIdAndShowIdForUpdate(command.seatId(), command.showId())
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    SeatReservation latestReservation = reservationRepo.findTopBySeatIdOrderByCreatedAtDescIdDesc(seat.getId()).orElse(null);
    expireIfNeeded(latestReservation, now);

    rejectIfOccupied(latestReservation, now);
    SeatReservation reservation = reservationRepo.save(
      SeatReservation.hold(command.showId(), command.seatId(), command.userId(), now.plus(holdDuration))
    );

    return ReservationResponse.from(reservation);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationResponse confirm(Long reservationId, Long userId) {
    Instant now = Instant.now(clock);
    SeatReservation reservation = reservationRepo.findById(reservationId)
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));

    seatRepo.findByIdAndShowIdForUpdate(reservation.getSeatId(), reservation.getShowId())
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    refreshAfterSeatLock(reservation);
    reservation.confirm(userId, now);
    return ReservationResponse.from(reservation);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationResponse cancel(Long reservationId, Long userId) {
    Instant now = Instant.now(clock);
    SeatReservation reservation = reservationRepo.findById(reservationId)
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));

    seatRepo.findByIdAndShowIdForUpdate(reservation.getSeatId(), reservation.getShowId())
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    refreshAfterSeatLock(reservation);
    reservation.cancel(userId, now);
    return ReservationResponse.from(reservation);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public boolean expireReservationIfNeeded(Long reservationId) {
    SeatReservation reservation = reservationRepo.findById(reservationId).orElse(null);
    if (reservation == null || reservation.getStatus() != SeatReservationStatus.HOLD) {
      return false;
    }

    seatRepo.findByIdAndShowIdForUpdate(reservation.getSeatId(), reservation.getShowId())
      .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));

    refreshAfterSeatLock(reservation);
    if (reservation.getStatus() != SeatReservationStatus.HOLD) {
      return false;
    }

    Instant now = Instant.now(clock);
    if (!reservation.isExpiredAt(now)) return false;

    reservation.expire(now);
    return true;
  }

  @Transactional(readOnly = true)
  public List<MyReservationResponse> getMyReservations(Long userId, Map<Long, String> seatNumberBySeatId) {
    return reservationRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
      .map(reservation -> new MyReservationResponse(
        reservation.getId(),
        reservation.getShowId(),
        reservation.getSeatId(),
        reservation.getUserId(),
        seatNumberBySeatId.get(reservation.getSeatId()),
        reservation.getStatus(),
        reservation.getHoldExpiresAt(),
        reservation.getConfirmedAt(),
        reservation.getCancelledAt(),
        reservation.getExpiredAt(),
        reservation.getCreatedAt()
      ))
      .toList();
  }

  private void expireIfNeeded(SeatReservation reservation, Instant now) {
    if (reservation != null && reservation.isExpiredAt(now)) {
      reservation.expire(now);
    }
  }

  private void rejectIfOccupied(SeatReservation latestReservation, Instant now) {
    if (latestReservation == null) return;

    if (latestReservation.getStatus() == SeatReservationStatus.HOLD && !latestReservation.isExpiredAt(now)) {
      throw new ReservationException(ReservationFailureReason.SEAT_ALREADY_HELD);
    }

    if (latestReservation.getStatus() == SeatReservationStatus.RESERVED) {
      throw new ReservationException(ReservationFailureReason.SEAT_ALREADY_RESERVED);
    }

  }

  private void refreshAfterSeatLock(SeatReservation reservation) {
    entityManager.refresh(reservation);
  }

}
