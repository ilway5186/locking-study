package com.ilway.couponsystem.coupon.issue.api;

import com.ilway.couponsystem.coupon.issue.CouponIssue;

import java.time.LocalDateTime;

public record CouponIssueResponse(
  Long issueId,
  Long couponEventId,
  Long userId,
  LocalDateTime issuedAt
) {

  public static CouponIssueResponse from(CouponIssue couponIssue) {
    return new CouponIssueResponse(
      couponIssue.id(),
      couponIssue.couponEvent().id(),
      couponIssue.userId(),
      couponIssue.issuedAt()
    );
  }

}
