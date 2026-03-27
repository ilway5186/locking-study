package com.ilway.coupon.coupon.event;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.api.AdminCouponEventStatisticsResponse;
import com.ilway.coupon.coupon.event.api.CouponEventResponse;
import com.ilway.coupon.coupon.event.api.CreateCouponEventRequest;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponEventService {

  private final CouponEventRepository couponEventRepository;
  private final CouponIssueRepository couponIssueRepository;

  @Transactional
  public CouponEventResponse create(CreateCouponEventRequest request) {
    CouponEvent couponEvent = CouponEvent.create(
        request.name(),
        request.totalQuantity(),
        request.startAt(),
        request.endAt()
    );
    CouponEvent saved = couponEventRepository.save(couponEvent);
    return CouponEventResponse.from(saved, LocalDateTime.now());
  }

  @Transactional(readOnly = true)
  public CouponEventResponse get(Long couponEventId) {
    CouponEvent couponEvent = couponEventRepository.findById(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));
    return CouponEventResponse.from(couponEvent, LocalDateTime.now());
  }

  @Transactional(readOnly = true)
  public AdminCouponEventStatisticsResponse getStatistics(Long couponEventId) {
    CouponEvent couponEvent = couponEventRepository.findById(couponEventId)
        .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));
    long successCount = couponIssueRepository.countByCouponEvent_Id(couponEventId);
    return AdminCouponEventStatisticsResponse.from(couponEvent, successCount, LocalDateTime.now());
  }
}
