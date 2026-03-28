package com.ilway.couponsystem.coupon.event.api;

import com.ilway.couponsystem.coupon.event.CouponEvent;

import java.time.LocalDateTime;

public record AdminCouponEventStatisticsResponse(
    Long couponEventId,
    String eventName,
    int totalQuantity,
    int issuedQuantity,
    int remainingQuantity,
    long successCount,
    String status
) {

  public static AdminCouponEventStatisticsResponse from(CouponEvent couponEvent, long successCount, LocalDateTime now) {
    return new AdminCouponEventStatisticsResponse(
        couponEvent.id(),
        couponEvent.name(),
        couponEvent.totalQuantity(),
        couponEvent.issuedQuantity(),
        couponEvent.remainingQuantity(),
        successCount,
        couponEvent.statusAt(now).name()
    );
  }
}
