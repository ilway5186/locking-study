package com.ilway.coupon.coupon.event.api;

import com.ilway.coupon.coupon.event.CouponEvent;
import java.time.LocalDateTime;

public record CouponEventResponse(
    Long couponEventId,
    String name,
    int totalQuantity,
    int issuedQuantity,
    int remainingQuantity,
    LocalDateTime startAt,
    LocalDateTime endAt,
    String status
) {

  public static CouponEventResponse from(CouponEvent couponEvent, LocalDateTime now) {
    return new CouponEventResponse(
        couponEvent.getId(),
        couponEvent.getName(),
        couponEvent.getTotalQuantity(),
        couponEvent.getIssuedQuantity(),
        couponEvent.remainingQuantity(),
        couponEvent.getStartAt(),
        couponEvent.getEndAt(),
        couponEvent.statusAt(now).name()
    );
  }
}
