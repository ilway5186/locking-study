package com.ilway.coupon.coupon.issue.request;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

  Optional<CouponIssueRequest> findByCouponEventIdAndUserIdAndIdempotencyKey(
      Long couponEventId,
      Long userId,
      String idempotencyKey
  );

  long countByCouponEventId(Long couponEventId);

  long countByCouponEventIdAndRequestStatus(Long couponEventId, CouponIssueRequestStatus requestStatus);

  @Query("select coalesce(sum(request.reusedCount), 0) from CouponIssueRequest request where request.couponEventId = :couponEventId")
  long sumReusedCountByCouponEventId(@Param("couponEventId") Long couponEventId);

  @Query("""
      select request.failureReason as failureReason, count(request) as count
      from CouponIssueRequest request
      where request.couponEventId = :couponEventId
        and request.requestStatus = :requestStatus
      group by request.failureReason
      """)
  List<CouponIssueFailureReasonCount> countFailureReasonsByCouponEventIdAndRequestStatus(
      @Param("couponEventId") Long couponEventId,
      @Param("requestStatus") CouponIssueRequestStatus requestStatus
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Transactional
  @Query("""
      update CouponIssueRequest request
      set request.reusedCount = request.reusedCount + 1
      where request.id = :requestId
      """)
  int incrementReusedCount(@Param("requestId") Long requestId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Transactional
  @Query("""
      update CouponIssueRequest request
      set request.requestStatus = :requestStatus,
          request.failureReason = null,
          request.issuedCouponIssueId = :issuedCouponIssueId
      where request.id = :requestId
      """)
  int markSuccess(
      @Param("requestId") Long requestId,
      @Param("requestStatus") CouponIssueRequestStatus requestStatus,
      @Param("issuedCouponIssueId") Long issuedCouponIssueId
  );

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Transactional
  @Query("""
      update CouponIssueRequest request
      set request.requestStatus = :requestStatus,
          request.failureReason = :failureReason,
          request.issuedCouponIssueId = null
      where request.id = :requestId
      """)
  int markFailed(
      @Param("requestId") Long requestId,
      @Param("requestStatus") CouponIssueRequestStatus requestStatus,
      @Param("failureReason") CouponIssueFailureReason failureReason
  );
}
