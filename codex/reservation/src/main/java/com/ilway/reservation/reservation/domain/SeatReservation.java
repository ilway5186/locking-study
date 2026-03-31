package com.ilway.reservation.reservation.domain;

import com.ilway.reservation.common.domain.BaseEntity;
import com.ilway.reservation.common.exception.ReservationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "seat_reservations",
    indexes = {
        @Index(name = "idx_reservation_seat_id", columnList = "seatId"),
        @Index(name = "idx_reservation_show_id", columnList = "showId"),
        @Index(name = "idx_reservation_user_id", columnList = "userId"),
        @Index(name = "idx_reservation_status_hold_expires_at", columnList = "status,holdExpiresAt")
    }
)
public class SeatReservation extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long showId;

  @Column(nullable = false)
  private Long seatId;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SeatReservationStatus status;

  @Column
  private Instant holdExpiresAt;

  @Column
  private Instant confirmedAt;

  @Column
  private Instant cancelledAt;

  @Column
  private Instant expiredAt;

  protected SeatReservation() {
  }

  private SeatReservation(Long showId, Long seatId, Long userId, Instant holdExpiresAt) {
    this.showId = showId;
    this.seatId = seatId;
    this.userId = userId;
    this.status = SeatReservationStatus.HOLD;
    this.holdExpiresAt = holdExpiresAt;
  }

  public static SeatReservation hold(Long showId, Long seatId, Long userId, Instant holdExpiresAt) {
    return new SeatReservation(showId, seatId, userId, holdExpiresAt);
  }

  public boolean isExpiredAt(Instant now) {
    return status == SeatReservationStatus.HOLD && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
  }

  public void confirm(Long userId, Instant now) {
    validateOwner(userId);
    if (status != SeatReservationStatus.HOLD) {
      throw new ReservationException(ReservationFailureReason.INVALID_RESERVATION_STATE);
    }
    if (isExpiredAt(now)) {
      expire(now);
      throw new ReservationException(ReservationFailureReason.HOLD_EXPIRED);
    }
    status = SeatReservationStatus.RESERVED;
    confirmedAt = now;
  }

  public void cancel(Long userId, Instant now) {
    validateOwner(userId);
    if (status != SeatReservationStatus.HOLD) {
      throw new ReservationException(ReservationFailureReason.INVALID_RESERVATION_STATE);
    }
    if (isExpiredAt(now)) {
      expire(now);
      throw new ReservationException(ReservationFailureReason.HOLD_EXPIRED);
    }
    status = SeatReservationStatus.CANCELLED;
    cancelledAt = now;
  }

  public void expire(Instant now) {
    if (status == SeatReservationStatus.HOLD) {
      status = SeatReservationStatus.EXPIRED;
      expiredAt = now;
    }
  }

  private void validateOwner(Long userId) {
    if (!this.userId.equals(userId)) {
      throw new ReservationException(ReservationFailureReason.FORBIDDEN_RESERVATION_ACCESS);
    }
  }

  public Long getId() {
    return id;
  }

  public Long getShowId() {
    return showId;
  }

  public Long getSeatId() {
    return seatId;
  }

  public Long getUserId() {
    return userId;
  }

  public SeatReservationStatus getStatus() {
    return status;
  }

  public Instant getHoldExpiresAt() {
    return holdExpiresAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Instant getExpiredAt() {
    return expiredAt;
  }
}
