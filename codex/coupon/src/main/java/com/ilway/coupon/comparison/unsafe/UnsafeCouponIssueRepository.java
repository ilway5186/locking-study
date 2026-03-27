package com.ilway.coupon.comparison.unsafe;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UnsafeCouponIssueRepository extends JpaRepository<UnsafeCouponIssue, Long> {

  boolean existsByCouponEventIdAndUserId(Long couponEventId, Long userId);

  long countByCouponEventId(Long couponEventId);
}
