package com.ilway.couponsystem.coupon.issue.request.registration;

import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequest;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponIssueRequestRegistrationWriter {

  private final CouponIssueRequestRepository repo;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public CouponIssueRequest createInProgress(String idempotencyKey, Long couponEventId, Long userId) {
    // 유니크 제약조건에 의해 이미 존재하는 쿠폰생성요청 건은 DB에 새롭게 생성되지 않음
    // 대신 saveAndFlush 호출 시 RuntimeException을 던지게 됨
    return repo.saveAndFlush(CouponIssueRequest.createInProgress(idempotencyKey, couponEventId, userId));
  }

}
