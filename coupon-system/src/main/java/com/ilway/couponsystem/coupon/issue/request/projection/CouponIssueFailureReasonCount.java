package com.ilway.couponsystem.coupon.issue.request.projection;

import com.ilway.couponsystem.coupon.issue.request.CouponIssueFailureReason;

public interface CouponIssueFailureReasonCount {

  CouponIssueFailureReason getFailureReason();

  long getCount();

}
