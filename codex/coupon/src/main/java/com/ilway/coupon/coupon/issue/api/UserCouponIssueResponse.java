package com.ilway.coupon.coupon.issue.api;

import com.ilway.coupon.coupon.issue.CouponIssue;
import java.time.LocalDateTime;

public record UserCouponIssueResponse(
    Long issueId,
    Long couponEventId,
    String eventName,
    LocalDateTime issuedAt
) {

  public static UserCouponIssueResponse from(CouponIssue couponIssue) {
    return new UserCouponIssueResponse(
        couponIssue.getId(),
        couponIssue.getCouponEvent().getId(),
        couponIssue.getCouponEvent().getName(),
        couponIssue.getIssuedAt()
    );
  }
}
