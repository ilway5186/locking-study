package com.ilway.couponsystem.coupon.event.api;

import com.ilway.couponsystem.coupon.event.CouponEvent;

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
      couponEvent.id(),
      couponEvent.name(),
      couponEvent.totalQuantity(),
      couponEvent.issuedQuantity(),
      couponEvent.remainingQuantity(),
      couponEvent.startAt(),
      couponEvent.endAt(),
      couponEvent.statusAt(now).name()
    );
  }

}
