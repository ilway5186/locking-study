package com.ilway.couponsystem.coupon.issue.processor;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import com.ilway.couponsystem.coupon.event.CouponEventRepository;
import com.ilway.couponsystem.coupon.issue.CouponIssue;
import com.ilway.couponsystem.coupon.issue.CouponIssueRepository;
import com.ilway.couponsystem.coupon.issue.api.CouponIssueResponse;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Qualifier("conditionalCouponIssueProcessor")
@RequiredArgsConstructor
public class ConditionalCouponIssueProcessor implements CouponIssueProcessor{

  private final CouponIssueRepository couponIssueRepo;
  private final CouponEventRepository couponEventRepo;
  private final CouponIssueRequestService couponIssueRequestService;
  private final ConditionalIssueFailureResolver failureResolver;

  @Override
  @Transactional
  public CouponIssueResponse issue(Long requestId, Long couponEventId, Long userId) {
    CouponEvent couponEvent = couponEventRepo.findById(couponEventId)
      .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now();
    couponEvent.validateIssuableAt(now);

    if (couponIssueRepo.existsByCouponEvent_IdAndUserId(couponEventId, userId)) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }

    int updatedRows = couponEventRepo.incrementIssuedQuantityIfAvailable(couponEventId);
    if (updatedRows == 0) {
      CouponEvent latestCouponEvent = couponEventRepo.findById(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));
      throw failureResolver.resolve(latestCouponEvent, now);
    }

    try {
      CouponIssue couponIssue = couponIssueRepo.saveAndFlush(CouponIssue.create(couponEvent, userId, now));
      couponIssueRequestService.markSuccess(requestId, couponIssue.id());
      return CouponIssueResponse.issuedFrom(couponIssue);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }
  }

}
