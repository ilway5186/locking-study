package com.ilway.couponsystem.coupon.issue;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import com.ilway.couponsystem.coupon.event.CouponEventRepository;
import com.ilway.couponsystem.coupon.issue.api.CouponIssueResponse;
import com.ilway.couponsystem.coupon.issue.api.IssueCouponRequest;
import com.ilway.couponsystem.coupon.issue.api.UserCouponIssueResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CouponIssueService {

  private final CouponIssueRepository couponIssueRepo;
  private final CouponEventRepository couponEventRepo;

  public CouponIssueResponse issue(Long couponEventId, Long userId) {
    CouponEvent couponEvent = couponEventRepo.findByIdForUpdate(couponEventId)
      .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now();
    couponEvent.validateIssuableAt(now);

    if (couponIssueRepo.existsByCouponEvent_IdAndUserId(couponEventId, userId)) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }

    couponEvent.issueOne(now);
    CouponIssue couponIssue = CouponIssue.create(couponEvent, userId, now);
    try {
      CouponIssue saved = couponIssueRepo.saveAndFlush(couponIssue);
      return CouponIssueResponse.from(saved);
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }
  }

  @Transactional(readOnly = true)
  public List<UserCouponIssueResponse> getUserIssues(Long userId) {
    return couponIssueRepo.findAllByUserIdOrderByIssuedAtDesc(userId).stream()
      .map(UserCouponIssueResponse::from)
      .toList();
  }

}

