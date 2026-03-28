package com.ilway.couponsystem.coupon.issue;

import com.ilway.couponsystem.coupon.event.CouponEvent;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Getter
@Accessors(fluent = true)
@NoArgsConstructor
@Entity
@Table(name = "coupon_issue",
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_coupon_issue_event_user", columnNames = {"coupon_event_id", "user_id"})
  },
  indexes = {
    @Index(name = "idx_coupon_issue_user_id", columnList = "user_id"),
    @Index(name = "idx_coupon_issue_event_id", columnList = "coupon_event_id")
  })
public class CouponIssue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "coupon_event_id", nullable = false)
  private CouponEvent couponEvent;

  @Column(name = "issued_at", nullable = false)
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
