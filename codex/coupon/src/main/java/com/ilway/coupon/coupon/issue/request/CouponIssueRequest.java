package com.ilway.coupon.coupon.issue.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "coupon_issue_request",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_coupon_issue_request_event_user_key",
            columnNames = {"coupon_event_id", "user_id", "idempotency_key"}
        )
    },
    indexes = {
        @Index(name = "idx_coupon_issue_request_event_status", columnList = "coupon_event_id,request_status"),
        @Index(name = "idx_coupon_issue_request_event_failure", columnList = "coupon_event_id,failure_reason")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "coupon_event_id", nullable = false)
  private Long couponEventId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "request_status", nullable = false, length = 20)
  private CouponIssueRequestStatus requestStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_reason", length = 50)
  private CouponIssueFailureReason failureReason;

  @Column(name = "issued_coupon_issue_id")
  private Long issuedCouponIssueId;

  @Column(name = "reused_count", nullable = false)
  private int reusedCount;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  private CouponIssueRequest(String idempotencyKey, Long couponEventId, Long userId) {
    this.idempotencyKey = idempotencyKey;
    this.couponEventId = couponEventId;
    this.userId = userId;
    this.requestStatus = CouponIssueRequestStatus.IN_PROGRESS;
    this.reusedCount = 0;
  }

  public static CouponIssueRequest createInProgress(String idempotencyKey, Long couponEventId, Long userId) {
    return new CouponIssueRequest(idempotencyKey, couponEventId, userId);
  }

  public void markSuccess(Long issuedCouponIssueId) {
    this.requestStatus = CouponIssueRequestStatus.SUCCESS;
    this.failureReason = null;
    this.issuedCouponIssueId = issuedCouponIssueId;
  }

  public void markFailed(CouponIssueFailureReason failureReason) {
    this.requestStatus = CouponIssueRequestStatus.FAILED;
    this.failureReason = failureReason;
    this.issuedCouponIssueId = null;
  }

  public void markReused() {
    this.reusedCount += 1;
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = LocalDateTime.now();
  }
}
