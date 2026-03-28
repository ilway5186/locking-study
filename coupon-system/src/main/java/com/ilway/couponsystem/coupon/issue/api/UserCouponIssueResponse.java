package com.ilway.couponsystem.coupon.issue.api;

import com.ilway.couponsystem.coupon.issue.CouponIssue;

import java.time.LocalDateTime;

public record UserCouponIssueResponse(
  Long issueId,
  Long couponEventId,
  String eventName,
  LocalDateTime issuedAt
) {

  public static UserCouponIssueResponse from(CouponIssue couponIssue) {
    return new UserCouponIssueResponse(
      couponIssue.id(),
      couponIssue.couponEvent().id(),
      couponIssue.couponEvent().name(),
      couponIssue.issuedAt()
    );
  }

}
