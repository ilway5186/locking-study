package com.ilway.coupon.comparison.unsafe;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import java.time.LocalDateTime;
import java.util.concurrent.locks.LockSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnsafeCouponIssueService {

  private static final long DEMO_RACE_WINDOW_NANOS = 20_000_000L;

  private final CouponEventRepository couponEventRepository;
  private final UnsafeCouponIssueRepository unsafeCouponIssueRepository;

  @Transactional
  public void issue(Long couponEventId, Long userId) {
    CouponEvent couponEvent = couponEventRepository.findById(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    LocalDateTime now = LocalDateTime.now();
    couponEvent.validateIssuableAt(now);

    if (unsafeCouponIssueRepository.existsByCouponEventIdAndUserId(couponEventId, userId)) {
      throw new BusinessException(ErrorCode.ALREADY_ISSUED);
    }

    // 비교용 코드다. race condition을 재현하기 위해 의도적으로 경쟁 구간을 넓힌다.
    LockSupport.parkNanos(DEMO_RACE_WINDOW_NANOS);

    couponEvent.issueOne(now);
    unsafeCouponIssueRepository.save(UnsafeCouponIssue.create(couponEventId, userId, now));
  }
}
