package com.ilway.couponsystem.coupon.issue;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository extends JpaRepository<CouponIssue, Long> {

  boolean existsByCouponEvent_IdAndUserId(Long couponEventId, Long userId);

  long countByCouponEvent_Id(Long couponEventId);

  @EntityGraph(attributePaths = "couponEvent")
  List<CouponIssue> findAllByUserIdOrderByIssuedAtDesc(Long userId);

  @EntityGraph(attributePaths = "couponEvent")
  Optional<CouponIssue> findById(Long id);

}
