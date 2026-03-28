package com.ilway.coupon.coupon.event.api;

import com.ilway.coupon.coupon.event.CouponEvent;
import java.util.Map;
import java.time.LocalDateTime;

public record AdminCouponEventStatisticsResponse(
    Long couponEventId,
    String eventName,
    int totalQuantity,
    int issuedQuantity,
    int remainingQuantity,
    long successCount,
    long totalRequestCount,
    long totalAttemptCount,
    long successRequestCount,
    long failureRequestCount,
    long reusedRequestCount,
    Map<String, Long> failureReasonCounts,
    String status
) {

  public static AdminCouponEventStatisticsResponse from(
      CouponEvent couponEvent,
      long successCount,
      long totalRequestCount,
      long successRequestCount,
      long failureRequestCount,
      long reusedRequestCount,
      Map<String, Long> failureReasonCounts,
      LocalDateTime now
  ) {
    return new AdminCouponEventStatisticsResponse(
        couponEvent.getId(),
        couponEvent.getName(),
        couponEvent.getTotalQuantity(),
        couponEvent.getIssuedQuantity(),
        couponEvent.remainingQuantity(),
        successCount,
        totalRequestCount,
        totalRequestCount + reusedRequestCount,
        successRequestCount,
        failureRequestCount,
        reusedRequestCount,
        failureReasonCounts,
        couponEvent.statusAt(now).name()
    );
  }
}
