package com.ilway.couponsystem.coupon.issue.api;

import com.ilway.couponsystem.coupon.issue.CouponIssue;
import com.ilway.couponsystem.coupon.issue.CouponIssueResultType;

import java.time.LocalDateTime;

public record CouponIssueResponse(
  Long issueId,
  Long couponEventId,
  Long userId,
  LocalDateTime issuedAt,
  CouponIssueResultType resultType
) {

  public static CouponIssueResponse issuedFrom(CouponIssue couponIssue) {
    return new CouponIssueResponse(
      couponIssue.id(),
      couponIssue.couponEvent().id(),
      couponIssue.userId(),
      couponIssue.issuedAt(),
      CouponIssueResultType.ISSUED
    );
  }

  public static CouponIssueResponse reusedFrom(CouponIssue couponIssue) {
    return new CouponIssueResponse(
      couponIssue.id(),
      couponIssue.couponEvent().id(),
      couponIssue.userId(),
      couponIssue.issuedAt(),
      CouponIssueResultType.REUSED
    );
  }

}
