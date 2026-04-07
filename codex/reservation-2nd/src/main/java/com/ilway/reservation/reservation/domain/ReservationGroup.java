package com.ilway.reservation.reservation.domain;

import com.ilway.reservation.common.domain.BaseEntity;
import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.seat.domain.Seat;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(
    name = "reservation_groups",
    indexes = {
        @Index(name = "idx_reservation_group_show_id", columnList = "showId"),
        @Index(name = "idx_reservation_group_user_id", columnList = "userId"),
        @Index(name = "idx_reservation_group_status_hold_expires_at", columnList = "status,holdExpiresAt")
    }
)
public class ReservationGroup extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long showId;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReservationGroupStatus status;

  @Column(nullable = false)
  private Instant holdExpiresAt;

  @Column
  private Instant paymentPendingAt;

  @Column
  private Instant reservedAt;

  @Column
  private Instant cancelledAt;

  @Column
  private Instant expiredAt;

  @OneToMany(mappedBy = "reservationGroup", cascade = CascadeType.ALL, orphanRemoval = false)
  private final List<ReservationGroupSeat> seats = new ArrayList<>();

  protected ReservationGroup() {
  }

  private ReservationGroup(Long showId, Long userId, Instant holdExpiresAt) {
    this.showId = showId;
    this.userId = userId;
    this.status = ReservationGroupStatus.HOLD;
    this.holdExpiresAt = holdExpiresAt;
  }

  public static ReservationGroup hold(Long showId, Long userId, Instant holdExpiresAt, List<Seat> seats) {
    ReservationGroup group = new ReservationGroup(showId, userId, holdExpiresAt);
    for (Seat seat : seats) {
      group.seats.add(ReservationGroupSeat.hold(group, seat.getId(), seat.getSeatNumber()));
    }
    return group;
  }

  public boolean isExpiredAt(Instant now) {
    return isPendingCompletion() && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
  }

  public void moveToPaymentPending(Long userId, Instant now) {
    validateOwner(userId);
    if (status != ReservationGroupStatus.HOLD) {
      throw new ReservationException(ReservationFailureReason.INVALID_GROUP_STATE);
    }
    if (isExpiredAt(now)) {
      expire(now);
      throw new ReservationException(ReservationFailureReason.HOLD_EXPIRED);
    }
    status = ReservationGroupStatus.PAYMENT_PENDING;
    paymentPendingAt = now;
    seats.forEach(ReservationGroupSeat::moveToPaymentPending);
  }

  public void reserve(Long userId, Instant now) {
    validateOwner(userId);
    if (status != ReservationGroupStatus.PAYMENT_PENDING) {
      throw new ReservationException(ReservationFailureReason.INVALID_GROUP_STATE);
    }
    if (isExpiredAt(now)) {
      expire(now);
      throw new ReservationException(ReservationFailureReason.HOLD_EXPIRED);
    }
    status = ReservationGroupStatus.RESERVED;
    reservedAt = now;
    seats.forEach(ReservationGroupSeat::reserve);
  }

  public void cancel(Long userId, Instant now) {
    validateOwner(userId);
    if (status != ReservationGroupStatus.HOLD) {
      throw new ReservationException(ReservationFailureReason.INVALID_GROUP_STATE);
    }
    if (isExpiredAt(now)) {
      expire(now);
      throw new ReservationException(ReservationFailureReason.HOLD_EXPIRED);
    }
    status = ReservationGroupStatus.CANCELLED;
    cancelledAt = now;
    seats.forEach(ReservationGroupSeat::cancel);
  }

  public void expire(Instant now) {
    if (!isPendingCompletion()) {
      return;
    }
    status = ReservationGroupStatus.EXPIRED;
    expiredAt = now;
    seats.forEach(ReservationGroupSeat::expire);
  }

  public boolean isPendingCompletion() {
    return status == ReservationGroupStatus.HOLD || status == ReservationGroupStatus.PAYMENT_PENDING;
  }

  private void validateOwner(Long userId) {
    if (!this.userId.equals(userId)) {
      throw new ReservationException(ReservationFailureReason.FORBIDDEN_GROUP_ACCESS);
    }
  }

  public Long getId() {
    return id;
  }

  public Long getShowId() {
    return showId;
  }

  public Long getUserId() {
    return userId;
  }

  public ReservationGroupStatus getStatus() {
    return status;
  }

  public Instant getHoldExpiresAt() {
    return holdExpiresAt;
  }

  public Instant getPaymentPendingAt() {
    return paymentPendingAt;
  }

  public Instant getReservedAt() {
    return reservedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Instant getExpiredAt() {
    return expiredAt;
  }

  public List<ReservationGroupSeat> getSeats() {
    return Collections.unmodifiableList(seats);
  }
}
