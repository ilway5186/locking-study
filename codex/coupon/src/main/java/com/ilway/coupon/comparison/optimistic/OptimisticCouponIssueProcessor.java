package com.ilway.coupon.comparison.optimistic;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.CouponIssue;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import com.ilway.coupon.coupon.issue.api.CouponIssueResponse;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestService;
import java.time.LocalDateTime;
import java.util.concurrent.locks.LockSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OptimisticCouponIssueProcessor {

  private static final long OPTIMISTIC_RACE_WINDOW_NANOS = 5_000_000L;

  private final CouponEventRepository couponEventRepository;
  private final CouponIssueRepository couponIssueRepository;
  private final CouponIssueRequestService couponIssueRequestService;

  @Transactional
  public CouponIssueResponse issue(Long requestId, Long couponEventId, Long userId) {
    CouponEvent couponEvent = couponEventRepository.findById(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now();
    couponEvent.validateIssuableAt(now);

    if (couponIssueRepository.existsByCouponEvent_IdAndUserId(couponEventId, userId)) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }

    // 학습용 충돌 재현을 위해 read-modify-write 간격을 약간 늘린다.
    LockSupport.parkNanos(OPTIMISTIC_RACE_WINDOW_NANOS);

    couponEvent.issueOne(now);

    try {
      CouponIssue couponIssue = couponIssueRepository.saveAndFlush(CouponIssue.create(couponEvent, userId, now));
      couponIssueRequestService.markSuccess(requestId, couponIssue.getId());
      return CouponIssueResponse.issuedFrom(couponIssue);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }
  }
}
