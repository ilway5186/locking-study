package com.ilway.couponsystem.coupon.issue.request.registration;

import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequest;

public record CouponIssueRequestRegistration(
  CouponIssueRequestRegistrationType type,
  CouponIssueRequest request
){}
