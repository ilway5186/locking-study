package com.ilway.coupon.coupon.event;

import java.time.LocalDateTime;

public enum CouponEventStatus {
  PENDING,
  OPEN,
  SOLD_OUT,
  CLOSED;

  public static CouponEventStatus from(CouponEvent couponEvent, LocalDateTime now) {
    if (now.isBefore(couponEvent.getStartAt())) {
      return PENDING;
    }
    if (now.isAfter(couponEvent.getEndAt())) {
      return CLOSED;
    }
    if (couponEvent.isSoldOut()) {
      return SOLD_OUT;
    }
    return OPEN;
  }
}
