package com.ilway.couponsystem.coupon.issue.request;

import com.ilway.couponsystem.coupon.issue.request.projection.CouponIssueFailureReasonCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestRepository extends JpaRepository<CouponIssueRequest, Long> {

  Optional<CouponIssueRequest> findByCouponEventIdAndUserIdAndIdempotencyKey(Long couponEventId, Long userId, String idempotencyKey);

  long countByCouponEventId(Long couponEventId);

  long countByCouponEventIdAndRequestStatus(Long couponEventId, CouponIssueRequestStatus requestStatus);

  @Query("SELECT COALESCE(SUM(cir.reusedCount), 0) FROM CouponIssueRequest cir WHERE cir.couponEventId = :couponEventId")
  long sumReusedCountByCouponEventId(@Param("couponEventId") Long couponEventId);

  @Query("""
       SELECT cir.failureReason as failureReason, COUNT(cir.id) as count
       FROM CouponIssueRequest cir
       WHERE cir.couponEventId = :couponEventId
         AND cir.requestStatus = :requestStatus
       GROUP BY cir.failureReason
    """)
  List<CouponIssueFailureReasonCount> countFailureReasonByCouponEventIdAndRequestStatus(
    @Param("couponEventId") Long couponEventId,
    @Param("requestStatus") CouponIssueRequestStatus requestStatus
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
      UPDATE CouponIssueRequest cir
        SET cir.reusedCount = cir.reusedCount + 1
        WHERE cir.id = :requestId
    """)
  int incrementReusedCount(@Param("requestId") Long requestId);

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
    UPDATE CouponIssueRequest cir
       SET cir.requestStatus = :requestStatus,
           cir.failureReason = null,
           cir.issuedCouponIssueId = :issuedCouponIssueId
    WHERE cir.id = :requestId
    """)
  void markSuccess(
    @Param("requestId") Long requestId,
    @Param("requestStatus") CouponIssueRequestStatus requestStatus,
    @Param("issuedCouponIssueId") Long issuedCouponIssueId
  );

  @Transactional
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("""
    UPDATE CouponIssueRequest cir
        SET cir.requestStatus = :requestStatus,
            cir.failureReason = :failureReason,
            cir.issuedCouponIssueId = null
    WHERE cir.id = :requestId
    """)
  int markFailed(
    @Param("requestId") Long requestId,
    @Param("requestStatus") CouponIssueRequestStatus requestStatus,
    @Param("failureReason") CouponIssueFailureReason failureReason
  );

}
