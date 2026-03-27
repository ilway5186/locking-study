package com.ilway.coupon.coupon.issue;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

  boolean existsByCouponEvent_IdAndUserId(Long couponEventId, Long userId);

  long countByCouponEvent_Id(Long couponEventId);

  @EntityGraph(attributePaths = "couponEvent")
  List<CouponIssue> findAllByUserIdOrderByIssuedAtDesc(Long userId);
}
