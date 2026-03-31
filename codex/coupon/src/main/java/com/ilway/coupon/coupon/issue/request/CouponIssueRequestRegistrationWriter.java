package com.ilway.coupon.coupon.issue.request;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponIssueRequestRegistrationWriter {

  private final CouponIssueRequestRepository couponIssueRequestRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CouponIssueRequest createInProgress(String idempotencyKey, Long couponEventId, Long userId) {
    return couponIssueRequestRepository.saveAndFlush(
        CouponIssueRequest.createInProgress(idempotencyKey, couponEventId, userId)
    );
  }
}
