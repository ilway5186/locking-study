package com.ilway.coupon.coupon.issue.request;

public record CouponIssueRequestRegistration(
    CouponIssueRequestRegistrationType type,
    CouponIssueRequest request
) {
}
