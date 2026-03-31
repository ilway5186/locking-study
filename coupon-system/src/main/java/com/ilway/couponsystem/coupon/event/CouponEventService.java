package com.ilway.couponsystem.coupon.event;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.api.AdminCouponEventStatisticsResponse;
import com.ilway.couponsystem.coupon.event.api.CouponEventResponse;
import com.ilway.couponsystem.coupon.event.api.CreateCouponEventRequest;
import com.ilway.couponsystem.coupon.issue.CouponIssueRepository;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class CouponEventService {

  private final CouponIssueRepository couponIssueRepo;
  private final CouponEventRepository couponEventRepo;
  private final CouponIssueRequestService couponIssueRequestService;

  public CouponEventResponse create(CreateCouponEventRequest request) {
    CouponEvent couponEvent = CouponEvent.create(request.name(), request.totalQuantity(), request.startAt(), request.endAt());

    CouponEvent saved = couponEventRepo.save(couponEvent);
    return CouponEventResponse.from(saved, LocalDateTime.now());
  }

  @Transactional(readOnly = true)
  public CouponEventResponse get(Long couponEventId) {
    CouponEvent couponEvent = couponEventRepo.findById(couponEventId)
      .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    return CouponEventResponse.from(couponEvent, LocalDateTime.now());
  }

  @Transactional(readOnly = true)
  public AdminCouponEventStatisticsResponse getStatistics(Long couponEventId) {
    CouponEvent couponEvent = couponEventRepo.findById(couponEventId)
      .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_EVENT_NOT_FOUND));

    long successCount = couponIssueRepo.countByCouponEvent_Id(couponEventId);
    long totalRequestCount = couponIssueRequestService.getTotalRequestCount(couponEventId);
    long successRequestCount = couponIssueRequestService.getSuccessRequestCount(couponEventId);
    long failureRequestCount = couponIssueRequestService.getFailureRequestCount(couponEventId);
    long reusedRequestCount = couponIssueRequestService.getReusedRequestCount(couponEventId);
    return AdminCouponEventStatisticsResponse.from(
      couponEvent,
      successCount,
      totalRequestCount,
      successRequestCount,
      failureRequestCount,
      reusedRequestCount,
      couponIssueRequestService.getFailureReasonCounts(couponEventId),
      LocalDateTime.now()
    );
  }

}
