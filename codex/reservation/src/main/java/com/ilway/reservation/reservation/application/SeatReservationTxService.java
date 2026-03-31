package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.api.dto.MyReservationResponse;
import com.ilway.reservation.reservation.api.dto.ReservationResponse;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.SeatReservation;
import com.ilway.reservation.reservation.domain.SeatReservationRepository;
import com.ilway.reservation.reservation.domain.SeatReservationStatus;
import com.ilway.reservation.seat.domain.Seat;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.Show;
import com.ilway.reservation.show.domain.ShowRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatReservationTxService {

  private final ShowRepository showRepository;
  private final SeatRepository seatRepository;
  private final SeatReservationRepository reservationRepository;
  private final BookingWindowPolicy bookingWindowPolicy;
  private final Clock clock;
  private final Duration holdDuration;

  public SeatReservationTxService(
      ShowRepository showRepository,
      SeatRepository seatRepository,
      SeatReservationRepository reservationRepository,
      BookingWindowPolicy bookingWindowPolicy,
      Clock clock,
      @Value("${reservation.hold.duration:PT5M}") Duration holdDuration
  ) {
    this.showRepository = showRepository;
    this.seatRepository = seatRepository;
    this.reservationRepository = reservationRepository;
    this.bookingWindowPolicy = bookingWindowPolicy;
    this.clock = clock;
    this.holdDuration = holdDuration;
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationResponse processHold(HoldSeatCommand command) {
    Instant now = Instant.now(clock);
    Show show = showRepository.findById(command.showId())
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND));
    bookingWindowPolicy.validateHoldable(show, now);

    // Seat row is the contention unit. Same-seat requests serialize here, different seats do not.
    Seat seat = seatRepository.findByIdAndShowIdForUpdate(command.seatId(), command.showId())
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    SeatReservation latestReservation = reservationRepository.findTopBySeatIdOrderByCreatedAtDescIdDesc(seat.getId()).orElse(null);
    expireIfNeeded(latestReservation, now);
    rejectIfOccupied(latestReservation, now);

    SeatReservation reservation = reservationRepository.save(
        SeatReservation.hold(command.showId(), command.seatId(), command.userId(), now.plus(holdDuration))
    );
    return toReservationResponse(reservation);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationResponse confirm(Long reservationId, Long userId) {
    Instant now = Instant.now(clock);
    SeatReservation reservation = reservationRepository.findById(reservationId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));

    seatRepository.findByIdAndShowIdForUpdate(reservation.getSeatId(), reservation.getShowId())
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    SeatReservation current = reservationRepository.findById(reservationId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));
    current.confirm(userId, now);
    return toReservationResponse(current);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationResponse cancel(Long reservationId, Long userId) {
    Instant now = Instant.now(clock);
    SeatReservation reservation = reservationRepository.findById(reservationId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));

    seatRepository.findByIdAndShowIdForUpdate(reservation.getSeatId(), reservation.getShowId())
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    SeatReservation current = reservationRepository.findById(reservationId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.RESERVATION_NOT_FOUND));
    current.cancel(userId, now);
    return toReservationResponse(current);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public boolean expireReservationIfNeeded(Long reservationId) {
    SeatReservation reservation = reservationRepository.findById(reservationId).orElse(null);
    if (reservation == null || reservation.getStatus() != SeatReservationStatus.HOLD) {
      return false;
    }

    seatRepository.findByIdAndShowIdForUpdate(reservation.getSeatId(), reservation.getShowId())
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND));

    SeatReservation current = reservationRepository.findById(reservationId).orElse(null);
    if (current == null || current.getStatus() != SeatReservationStatus.HOLD) {
      return false;
    }
    Instant now = Instant.now(clock);
    if (!current.isExpiredAt(now)) {
      return false;
    }
    current.expire(now);
    return true;
  }

  @Transactional(readOnly = true)
  public List<MyReservationResponse> getMyReservations(Long userId, Map<Long, String> seatNumberBySeatId) {
    return reservationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
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

  public ReservationResponse toReservationResponse(SeatReservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        reservation.getShowId(),
        reservation.getSeatId(),
        reservation.getUserId(),
        reservation.getStatus(),
        reservation.getHoldExpiresAt(),
        reservation.getConfirmedAt(),
        reservation.getCancelledAt(),
        reservation.getExpiredAt()
    );
  }

  private void expireIfNeeded(SeatReservation reservation, Instant now) {
    if (reservation != null && reservation.isExpiredAt(now)) {
      reservation.expire(now);
    }
  }

  private void rejectIfOccupied(SeatReservation latestReservation, Instant now) {
    if (latestReservation == null) {
      return;
    }
    if (latestReservation.getStatus() == SeatReservationStatus.HOLD && !latestReservation.isExpiredAt(now)) {
      throw new ReservationException(ReservationFailureReason.SEAT_ALREADY_HELD);
    }
    if (latestReservation.getStatus() == SeatReservationStatus.RESERVED) {
      throw new ReservationException(ReservationFailureReason.SEAT_ALREADY_RESERVED);
    }
  }
}
