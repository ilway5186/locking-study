package com.ilway.couponsystem.coupon.issue.request;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.issue.request.projection.CouponIssueFailureReasonCount;
import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistration;
import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistrationWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestStatus.FAILED;
import static com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestStatus.SUCCESS;
import static com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistrationType.*;

@Service
@RequiredArgsConstructor
public class CouponIssueRequestService {

  private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

  private final CouponIssueRequestRepository repo;
  private final CouponIssueRequestRegistrationWriter registrationWriter;

  @Transactional(readOnly = true)
  public CouponIssueRequest getById(Long requestId) {
    return repo.findById(requestId)
      .orElseThrow(() -> new IllegalStateException("쿠폰 발급 요청 이력을 찾을 수 없습니다. requestId=" + requestId));
  }

  public CouponIssueRequestRegistration register(Long couponEventId, Long userId, String idempotency) {
    String resolveIdempotencyKey = resolveIdempotencyKey(idempotency);
    try {
      CouponIssueRequest request = registrationWriter.createInProgress(resolveIdempotencyKey, couponEventId, userId);
      return new CouponIssueRequestRegistration(NEW, request);
    } catch (RuntimeException e) {
      return reuseRegisteredRequest(couponEventId, userId, resolveIdempotencyKey, e);
    }
  }

  @Transactional
  public void markSuccess(Long requestId, Long issuedCouponIssueId) {
    repo.markSuccess(requestId, SUCCESS, issuedCouponIssueId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(Long requestId, CouponIssueFailureReason failureReason) {
    repo.markFailed(requestId, FAILED, failureReason);
  }

  @Transactional(readOnly = true)
  public Map<String, Long> getFailureReasonCounts(Long couponEventId) {
    return repo.countFailureReasonByCouponEventIdAndRequestStatus(couponEventId, FAILED).stream()
      .collect(Collectors.toMap(
        count -> count.getFailureReason().name(),
        CouponIssueFailureReasonCount::getCount
      ));
  }

  @Transactional(readOnly = true)
  public long getTotalRequestCount(Long couponEventId) {
    return repo.countByCouponEventId(couponEventId);
  }

  @Transactional(readOnly = true)
  public long getSuccessRequestCount(Long couponEventId) {
    return repo.countByCouponEventIdAndRequestStatus(couponEventId, SUCCESS);
  }

  @Transactional(readOnly = true)
  public long getFailureRequestCount(Long couponEventId) {
    return repo.countByCouponEventIdAndRequestStatus(couponEventId, FAILED);
  }

  @Transactional(readOnly = true)
  public long getReusedRequestCount(Long couponEventId) {
    return repo.sumReusedCountByCouponEventId(couponEventId);
  }

  protected CouponIssueRequestRegistration reuseRegisteredRequest(Long couponEventId, Long userId, String idempotencyKey, RuntimeException e) {
    CouponIssueRequest existingRequest = repo
      .findByCouponEventIdAndUserIdAndIdempotencyKey(couponEventId, userId, idempotencyKey)
      .orElseThrow(() -> e);

    existingRequest.markReused();
    repo.incrementReusedCount(existingRequest.id());

    return switch (existingRequest.requestStatus()) {
      case SUCCESS -> new CouponIssueRequestRegistration(SUCCESS_REPLAY, existingRequest);
      case FAILED -> new CouponIssueRequestRegistration(FAILURE_REPLAY, existingRequest);
      case IN_PROGRESS -> new CouponIssueRequestRegistration(IN_PROGRESS_DUPLICATE, existingRequest);
    };
  }

  private String resolveIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null) return UUID.randomUUID().toString();

    String trimmed = idempotencyKey.trim();
    if (trimmed.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 비어있을 수 없습니다.");
    }
    if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key는 최대 " + MAX_IDEMPOTENCY_KEY_LENGTH + "자까지 허용됩니다.");
    }
    return trimmed;
  }

}
