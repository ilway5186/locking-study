package com.ilway.couponsystem.coupon.issue;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import com.ilway.couponsystem.coupon.event.CouponEventRepository;
import com.ilway.couponsystem.coupon.issue.api.CouponIssueResponse;
import com.ilway.couponsystem.coupon.issue.api.IssueCouponRequest;
import com.ilway.couponsystem.coupon.issue.api.UserCouponIssueResponse;
import com.ilway.couponsystem.coupon.issue.processor.CouponIssueProcessor;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueFailureReason;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequest;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestService;
import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
public class CouponIssueService {

  private final CouponIssueRepository couponIssueRepo;
  private final CouponIssueProcessor couponIssueProcessor;
  private final CouponIssueRequestService couponIssueRequestService;

  public CouponIssueService(
    @Qualifier("conditionalCouponIssueProcessor") CouponIssueProcessor couponIssueProcessor,
    CouponIssueRepository couponIssueRepo,
    CouponIssueRequestService couponIssueRequestService
  ) {

    this.couponIssueProcessor = couponIssueProcessor;
    this.couponIssueRepo = couponIssueRepo;
    this.couponIssueRequestService = couponIssueRequestService;
  }

  public CouponIssueResponse issue(Long couponEventId, Long userId, String idempotencyKey) {
    CouponIssueRequestRegistration registration = couponIssueRequestService.register(couponEventId, userId, idempotencyKey);

    return switch (registration.type()) {
      case NEW -> executeNewRequest(registration.request().id(), couponEventId, userId);
      case SUCCESS_REPLAY -> reuseSuccess(registration.request());
      case FAILURE_REPLAY -> throw registration.request().failureReason().toBusinessException();
      case IN_PROGRESS_DUPLICATE -> throw new BusinessException(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
    };
  }

  private CouponIssueResponse executeNewRequest(Long requestId, Long couponEventId, Long userId) {
    try {
      return couponIssueProcessor.issue(requestId, couponEventId, userId);
    } catch (BusinessException e) {
      couponIssueRequestService.markFailed(requestId, CouponIssueFailureReason.from(e.errorCode()));
      throw e;
    } catch (Exception e) {
      couponIssueRequestService.markFailed(requestId, CouponIssueFailureReason.INTERNAL_ERROR);
      throw e;
    }
  }

  private CouponIssueResponse reuseSuccess(CouponIssueRequest request) {
    Long issueId = request.issuedCouponIssueId();
    CouponIssue couponIssue = couponIssueRepo.findById(issueId)
      .orElseThrow(() -> new IllegalStateException("기존 발급 결과를 찾을 수 없습니다. issueId=" + issueId));

    return CouponIssueResponse.reusedFrom(couponIssue);
  }

  @Transactional(readOnly = true)
  public List<UserCouponIssueResponse> getUserIssues(Long userId) {
    return couponIssueRepo.findAllByUserIdOrderByIssuedAtDesc(userId).stream()
      .map(UserCouponIssueResponse::from)
      .toList();
  }

}

