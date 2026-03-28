package com.ilway.couponsystem.coupon.issue;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

  boolean existsByCouponEvent_IdAndUserId(Long couponEventId, Long userId);

  long countByCouponEvent_Id(Long couponEventId);

  @EntityGraph(attributePaths = "couponEvent")
  List<CouponIssue> findAllByUserIdOrderByIssuedAtDesc(Long userId);

}
