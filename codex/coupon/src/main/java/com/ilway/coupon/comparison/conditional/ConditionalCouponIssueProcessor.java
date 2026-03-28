package com.ilway.coupon.comparison.conditional;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.CouponIssue;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import com.ilway.coupon.coupon.issue.api.CouponIssueResponse;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConditionalCouponIssueProcessor {

  private final CouponEventRepository couponEventRepository;
  private final CouponIssueRepository couponIssueRepository;
  private final CouponIssueRequestService couponIssueRequestService;
  private final ConditionalIssueFailureResolver conditionalIssueFailureResolver;

  @Transactional
  public CouponIssueResponse issue(Long requestId, Long couponEventId, Long userId) {
    CouponEvent couponEvent = couponEventRepository.findById(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now();
    couponEvent.validateIssuableAt(now);

    if (couponIssueRepository.existsByCouponEvent_IdAndUserId(couponEventId, userId)) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }

    int updatedRows = couponEventRepository.incrementIssuedQuantityIfAvailable(couponEventId);
    if (updatedRows == 0) {
      CouponEvent latestCouponEvent = couponEventRepository.findById(couponEventId)
          .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));
      throw conditionalIssueFailureResolver.resolve(latestCouponEvent, now);
    }

    try {
      CouponIssue couponIssue = couponIssueRepository.saveAndFlush(CouponIssue.create(couponEvent, userId, now));
      couponIssueRequestService.markSuccess(requestId, couponIssue.getId());
      return CouponIssueResponse.issuedFrom(couponIssue);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }
  }
}
