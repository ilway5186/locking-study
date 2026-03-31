package com.ilway.coupon.coupon.issue.request;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueRequestService {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final CouponIssueRequestRepository couponIssueRequestRepository;
  private final CouponIssueRequestRegistrationWriter couponIssueRequestRegistrationWriter;

  public CouponIssueRequestRegistration register(Long couponEventId, Long userId, String idempotencyKey) {
    String resolvedIdempotencyKey = resolveIdempotencyKey(idempotencyKey);

    try {
      CouponIssueRequest request = couponIssueRequestRegistrationWriter.createInProgress(resolvedIdempotencyKey, couponEventId, userId);
      return new CouponIssueRequestRegistration(CouponIssueRequestRegistrationType.NEW, request);
    } catch (RuntimeException exception) {
      return reuseRegisteredRequest(couponEventId, userId, resolvedIdempotencyKey, exception);
    }
  }

  @Transactional
  public void markSuccess(Long requestId, Long issuedCouponIssueId) {
    couponIssueRequestRepository.markSuccess(requestId, CouponIssueRequestStatus.SUCCESS, issuedCouponIssueId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long requestId, CouponIssueFailureReason failureReason) {
    couponIssueRequestRepository.markFailed(requestId, CouponIssueRequestStatus.FAILED, failureReason);
  }

  @Transactional(readOnly = true)
  public CouponIssueRequest getById(Long requestId) {
    return couponIssueRequestRepository.findById(requestId)
        .orElseThrow(() -> new IllegalStateException("쿠폰 발급 요청 이력을 찾을 수 없습니다. requestId=" + requestId));
  }

  @Transactional(readOnly = true)
  public Map<String, Long> getFailureReasonCounts(Long couponEventId) {
    return couponIssueRequestRepository
        .countFailureReasonsByCouponEventIdAndRequestStatus(couponEventId, CouponIssueRequestStatus.FAILED)
        .stream()
        .collect(Collectors.toMap(
            count -> count.getFailureReason().name(),
            CouponIssueFailureReasonCount::getCount
        ));
  }

  @Transactional(readOnly = true)
  public long getTotalRequestCount(Long couponEventId) {
    return couponIssueRequestRepository.countByCouponEventId(couponEventId);
  }

  @Transactional(readOnly = true)
  public long getSuccessRequestCount(Long couponEventId) {
    return couponIssueRequestRepository.countByCouponEventIdAndRequestStatus(couponEventId, CouponIssueRequestStatus.SUCCESS);
  }

  @Transactional(readOnly = true)
  public long getFailureRequestCount(Long couponEventId) {
    return couponIssueRequestRepository.countByCouponEventIdAndRequestStatus(couponEventId, CouponIssueRequestStatus.FAILED);
  }

  @Transactional(readOnly = true)
  public long getReusedRequestCount(Long couponEventId) {
    return couponIssueRequestRepository.sumReusedCountByCouponEventId(couponEventId);
  }

  protected CouponIssueRequestRegistration reuseRegisteredRequest(Long couponEventId, Long userId, String idempotencyKey, RuntimeException exception) {
    CouponIssueRequest existingRequest = couponIssueRequestRepository
        .findByCouponEventIdAndUserIdAndIdempotencyKey(couponEventId, userId, idempotencyKey)
        .orElseThrow(() -> exception);
    existingRequest.markReused();
    couponIssueRequestRepository.incrementReusedCount(existingRequest.getId());
    return switch (existingRequest.getRequestStatus()) {
      case SUCCESS -> new CouponIssueRequestRegistration(CouponIssueRequestRegistrationType.SUCCESS_REPLAY, existingRequest);
      case FAILED -> new CouponIssueRequestRegistration(CouponIssueRequestRegistrationType.FAILURE_REPLAY, existingRequest);
      case IN_PROGRESS -> new CouponIssueRequestRegistration(CouponIssueRequestRegistrationType.IN_PROGRESS_DUPLICATE, existingRequest);
    };
  }

  private String resolveIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null) {
      return UUID.randomUUID().toString();
    }

    String normalized = idempotencyKey.trim();
    if (normalized.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 비어 있을 수 없습니다.");
    }
    if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 100자를 초과할 수 없습니다.");
    }
    return normalized;
  }
}
