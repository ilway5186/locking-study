package com.ilway.coupon.coupon.issue;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.api.CouponIssueResponse;
import com.ilway.coupon.coupon.issue.api.UserCouponIssueResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueService {

  private final CouponEventRepository couponEventRepository;
  private final CouponIssueRepository couponIssueRepository;

  @Transactional
  public CouponIssueResponse issue(Long couponEventId, Long userId) {
    // 이 SELECT ... FOR UPDATE 시점부터 coupon_event 행에 배타 락이 걸리고, 트랜잭션 종료 시 풀린다.
    CouponEvent couponEvent = couponEventRepository.findByIdForUpdate(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now();
    couponEvent.validateIssuableAt(now);

    if (couponIssueRepository.existsByCouponEvent_IdAndUserId(couponEventId, userId)) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }

    couponEvent.issueOne(now);

    try {
      // unique index는 애플리케이션 체크를 뚫고 들어온 중복 발급을 DB 레벨에서 마지막으로 차단한다.
      CouponIssue couponIssue = couponIssueRepository.saveAndFlush(CouponIssue.create(couponEvent, userId, now));
      return CouponIssueResponse.from(couponIssue);
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }
  }

  @Transactional(readOnly = true)
  public List<UserCouponIssueResponse> getUserIssues(Long userId) {
    return couponIssueRepository.findAllByUserIdOrderByIssuedAtDesc(userId).stream()
        .map(UserCouponIssueResponse::from)
        .toList();
  }
}
