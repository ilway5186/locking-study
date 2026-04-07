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
    name = "reservation_group_requests",
    indexes = {
        @Index(name = "idx_group_request_show_status", columnList = "showId,requestStatus"),
        @Index(name = "idx_group_request_show_failure_reason", columnList = "showId,failureReason")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_request_action_user_idempotency", columnNames = {"action", "userId", "idempotencyKey"})
    }
)
public class ReservationGroupRequest extends BaseEntity {

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
  private Long userId;

  @Column(nullable = false, length = 300)
  private String normalizedSeatSelectionKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReservationRequestStatus requestStatus;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private ReservationFailureReason failureReason;

  @Column
  private Long reservationGroupId;

  @Column(nullable = false)
  private long reusedCount;

  protected ReservationGroupRequest() {
  }

  private ReservationGroupRequest(
      ReservationRequestAction action,
      String idempotencyKey,
      Long showId,
      Long userId,
      String normalizedSeatSelectionKey
  ) {
    this.action = action;
    this.idempotencyKey = idempotencyKey;
    this.showId = showId;
    this.userId = userId;
    this.normalizedSeatSelectionKey = normalizedSeatSelectionKey;
    this.requestStatus = ReservationRequestStatus.IN_PROGRESS;
    this.reusedCount = 0L;
  }

  public static ReservationGroupRequest hold(
      String idempotencyKey,
      Long showId,
      Long userId,
      String normalizedSeatSelectionKey
  ) {
    return new ReservationGroupRequest(
        ReservationRequestAction.HOLD,
        idempotencyKey,
        showId,
        userId,
        normalizedSeatSelectionKey
    );
  }

  public boolean matchesPayload(Long showId, Long userId, String normalizedSeatSelectionKey) {
    return this.showId.equals(showId)
        && this.userId.equals(userId)
        && this.normalizedSeatSelectionKey.equals(normalizedSeatSelectionKey);
  }

  public void markSucceeded(Long reservationGroupId) {
    this.requestStatus = ReservationRequestStatus.SUCCEEDED;
    this.failureReason = null;
    this.reservationGroupId = reservationGroupId;
  }

  public void markFailed(ReservationFailureReason failureReason) {
    this.requestStatus = ReservationRequestStatus.FAILED;
    this.failureReason = failureReason;
    this.reservationGroupId = null;
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

  public Long getUserId() {
    return userId;
  }

  public String getNormalizedSeatSelectionKey() {
    return normalizedSeatSelectionKey;
  }

  public ReservationRequestStatus getRequestStatus() {
    return requestStatus;
  }

  public ReservationFailureReason getFailureReason() {
    return failureReason;
  }

  public Long getReservationGroupId() {
    return reservationGroupId;
  }

  public long getReusedCount() {
    return reusedCount;
  }
}
