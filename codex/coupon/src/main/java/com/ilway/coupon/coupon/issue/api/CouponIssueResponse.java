package com.ilway.coupon.coupon.issue.api;

import com.ilway.coupon.coupon.issue.CouponIssue;
import com.ilway.coupon.coupon.issue.CouponIssueResultType;
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
        couponIssue.getId(),
        couponIssue.getCouponEvent().getId(),
        couponIssue.getUserId(),
        couponIssue.getIssuedAt(),
        CouponIssueResultType.ISSUED
    );
  }

  public static CouponIssueResponse reusedFrom(CouponIssue couponIssue) {
    return new CouponIssueResponse(
        couponIssue.getId(),
        couponIssue.getCouponEvent().getId(),
        couponIssue.getUserId(),
        couponIssue.getIssuedAt(),
        CouponIssueResultType.REUSED
    );
  }
}
