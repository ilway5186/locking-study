package com.ilway.reservationsystem.reservation.domain;

import com.ilway.reservationsystem.common.domain.BaseEntity;
import com.ilway.reservationsystem.common.exception.ReservationException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "seat_reservation",
  indexes = {
    @Index(name = "idx_reservation_seat_id", columnList = "seat_id"),
    @Index(name = "idx_reservation_show_id", columnList = "show_id"),
    @Index(name = "idx_reservation_user_id", columnList = "user_id"),
    @Index(name = "idx_reservation_status_hold_expires_at", columnList = "status,holdExpiresAt")
  }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatReservation extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "show_id", nullable = false)
  private Long showId;

  @Column(name = "seat_id", nullable = false)
  private Long seatId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SeatReservationStatus status;

  @Column(name = "hold_expires_at")
  private Instant holdExpiresAt;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "expired_at")
  private Instant expiredAt;

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
    return status == SeatReservationStatus.HOLD
      && expiredAt != null
      && now.isAfter(expiredAt);
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
    if (status != SeatReservationStatus.RESERVED) {
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

  }

  private void validateOwner(Long userId) {
    if (!this.userId.equals(userId)) {
      throw new ReservationException(ReservationFailureReason.FORBIDDEN_RESERVATION_ACCESS);
    }
  }


}
