package com.ilway.coupon.coupon.issue;

import com.ilway.coupon.coupon.event.CouponEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "coupon_issue",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_issue_event_user", columnNames = {"coupon_event_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_coupon_issue_user_id", columnList = "user_id"),
        @Index(name = "idx_coupon_issue_event_id", columnList = "coupon_event_id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coupon_event_id", nullable = false)
  private CouponEvent couponEvent;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDateTime issuedAt;

  private CouponIssue(CouponEvent couponEvent, Long userId, LocalDateTime issuedAt) {
    this.couponEvent = couponEvent;
    this.userId = userId;
    this.issuedAt = issuedAt;
  }

  public static CouponIssue create(CouponEvent couponEvent, Long userId, LocalDateTime issuedAt) {
    return new CouponIssue(couponEvent, userId, issuedAt);
  }
}
