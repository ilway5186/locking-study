package com.ilway.couponsystem.coupon.issue.processor;

import com.ilway.couponsystem.coupon.issue.api.CouponIssueResponse;

public interface CouponIssueProcessor {

  CouponIssueResponse issue(Long requestId, Long couponEventId, Long userId);

}
