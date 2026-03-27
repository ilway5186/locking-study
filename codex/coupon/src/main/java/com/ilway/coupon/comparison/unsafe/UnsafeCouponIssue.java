package com.ilway.coupon.comparison.unsafe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "unsafe_coupon_issue",
    indexes = {
        @Index(name = "idx_unsafe_coupon_issue_event_user", columnList = "coupon_event_id,user_id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnsafeCouponIssue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "coupon_event_id", nullable = false)
  private Long couponEventId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDateTime issuedAt;

  private UnsafeCouponIssue(Long couponEventId, Long userId, LocalDateTime issuedAt) {
    this.couponEventId = couponEventId;
    this.userId = userId;
    this.issuedAt = issuedAt;
  }

  public static UnsafeCouponIssue create(Long couponEventId, Long userId, LocalDateTime issuedAt) {
    return new UnsafeCouponIssue(couponEventId, userId, issuedAt);
  }
}
