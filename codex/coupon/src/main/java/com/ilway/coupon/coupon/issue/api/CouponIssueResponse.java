package com.ilway.coupon.coupon.issue.api;

import com.ilway.coupon.coupon.issue.CouponIssue;
import java.time.LocalDateTime;

public record CouponIssueResponse(
    Long issueId,
    Long couponEventId,
    Long userId,
    LocalDateTime issuedAt
) {

  public static CouponIssueResponse from(CouponIssue couponIssue) {
    return new CouponIssueResponse(
        couponIssue.getId(),
        couponIssue.getCouponEvent().getId(),
        couponIssue.getUserId(),
        couponIssue.getIssuedAt()
    );
  }
}
