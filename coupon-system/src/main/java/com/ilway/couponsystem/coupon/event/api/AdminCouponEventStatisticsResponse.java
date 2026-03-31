package com.ilway.couponsystem.coupon.event.api;

import com.ilway.couponsystem.coupon.event.CouponEvent;

import java.time.LocalDateTime;
import java.util.Map;

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
      couponEvent.id(),
      couponEvent.name(),
      couponEvent.totalQuantity(),
      couponEvent.issuedQuantity(),
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
