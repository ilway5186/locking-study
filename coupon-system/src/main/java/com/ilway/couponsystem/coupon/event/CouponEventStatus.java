package com.ilway.couponsystem.coupon.event;

import java.time.LocalDateTime;

public enum CouponEventStatus {

  PENDING,
  OPEN,
  SOLD_OUT,
  CLOSED;

  public static CouponEventStatus from(CouponEvent couponEvent, LocalDateTime now) {
    if (now.isBefore(couponEvent.startAt())) {
      return PENDING;
    }
    if (now.isAfter(couponEvent.endAt())) {
      return CLOSED;
    }
    if (couponEvent.isSoldOut()) {
      return SOLD_OUT;
    }
    return OPEN;
  }

}
