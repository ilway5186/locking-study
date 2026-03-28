package com.ilway.coupon.coupon.issue.request;

public record CouponIssueRequestClaim(
    CouponIssueRequestClaimType type,
    Long requestId,
    CouponIssueRequest request
) {
}
