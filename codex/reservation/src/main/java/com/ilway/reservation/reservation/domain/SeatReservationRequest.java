package com.ilway.reservation.reservation.domain;

import com.ilway.reservation.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "seat_reservation_requests",
    indexes = {
        @Index(name = "idx_request_status", columnList = "requestStatus"),
        @Index(name = "idx_request_failure_reason", columnList = "failureReason")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_request_action_user_idempotency", columnNames = {"action", "userId", "idempotencyKey"})
    }
)
public class SeatReservationRequest extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationRequestAction action;

  @Column(nullable = false, length = 100)
  private String idempotencyKey;

  @Column(nullable = false)
  private Long showId;

  @Column(nullable = false)
  private Long seatId;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationRequestStatus requestStatus;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private ReservationFailureReason failureReason;

  @Column
  private Long reservationId;

  @Column(nullable = false)
  private long reusedCount;

  protected SeatReservationRequest() {
  }

  private SeatReservationRequest(
      ReservationRequestAction action,
      String idempotencyKey,
      Long showId,
      Long seatId,
      Long userId
  ) {
    this.action = action;
    this.idempotencyKey = idempotencyKey;
    this.showId = showId;
    this.seatId = seatId;
    this.userId = userId;
    this.requestStatus = ReservationRequestStatus.IN_PROGRESS;
    this.reusedCount = 0L;
  }

  public static SeatReservationRequest hold(String idempotencyKey, Long showId, Long seatId, Long userId) {
    return new SeatReservationRequest(ReservationRequestAction.HOLD, idempotencyKey, showId, seatId, userId);
  }

  public boolean matchesPayload(Long showId, Long seatId, Long userId) {
    return this.showId.equals(showId) && this.seatId.equals(seatId) && this.userId.equals(userId);
  }

  public void markSucceeded(Long reservationId) {
    this.requestStatus = ReservationRequestStatus.SUCCEEDED;
    this.reservationId = reservationId;
    this.failureReason = null;
  }

  public void markFailed(ReservationFailureReason failureReason) {
    this.requestStatus = ReservationRequestStatus.FAILED;
    this.failureReason = failureReason;
  }

  public void increaseReusedCount() {
    this.reusedCount += 1;
  }

  public Long getId() {
    return id;
  }

  public ReservationRequestAction getAction() {
    return action;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
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

  public ReservationRequestStatus getRequestStatus() {
    return requestStatus;
  }

  public ReservationFailureReason getFailureReason() {
    return failureReason;
  }

  public Long getReservationId() {
    return reservationId;
  }

  public long getReusedCount() {
    return reusedCount;
  }
}
