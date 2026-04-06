package com.ilway.reservationsystem.reservation.domain;

import com.ilway.reservationsystem.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "seat_reservation_requests",
  indexes = {
    @Index(name = "idx_request_status", columnList = "request_status"),
    @Index(name = "idx_request_failure_reason", columnList = "failure_reason")
  },
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_request_action_user_idempotency", columnNames = {"action", "userId", "idempotency"})
  }
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SeatReservationRequest extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "action", nullable = false, length = 20)
  private ReservationRequestAction action;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "show_id", nullable = false)
  private Long showId;

  @Column(name = "seat_id", nullable = false)
  private Long seatId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_status", nullable = false, length = 20)
  private ReservationRequestStatus requestStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_reason", length = 40)
  private ReservationFailureReason failureReason;

  @Column(name = "reservation_id")
  private Long reservationId;

  @Column(name = "reused_count", nullable = false)
  private long reusedCount;

  private SeatReservationRequest(ReservationRequestAction action, String idempotencyKey, Long showId, Long seatId, Long userId) {
    this.action = action;
    this.idempotencyKey = idempotencyKey;
    this.showId = showId;
    this.seatId = seatId;
    this.userId = userId;
  }

  public static SeatReservationRequest hold(String idempotencyKey, Long showId, Long seatId, Long userId) {
    return new SeatReservationRequest(ReservationRequestAction.HOLD, idempotencyKey, showId, seatId, userId);
  }

  public void markSucceeded(Long reservationId) {
    this.requestStatus = ReservationRequestStatus.SUCCEEDED;
    this.reservationId = reservationId;
    this.failureReason = null;
  }

  public void markFailed(ReservationFailureReason reason) {
    this.requestStatus = ReservationRequestStatus.FAILED;
    this.failureReason = reason;
  }

  public void increaseReusedCount() {
    this.reusedCount += 1;
  }

  public boolean matchesPayload(Long showId, Long seatId, Long userId) {
    return this.showId.equals(showId)
      && this.seatId.equals(seatId)
      && this.userId.equals(userId);
  }

}
