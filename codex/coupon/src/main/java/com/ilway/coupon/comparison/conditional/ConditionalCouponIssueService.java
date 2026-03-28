package com.ilway.coupon.comparison.conditional;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.issue.CouponIssue;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import com.ilway.coupon.coupon.issue.api.CouponIssueResponse;
import com.ilway.coupon.coupon.issue.request.CouponIssueFailureReason;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequest;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestClaim;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConditionalCouponIssueService {

  private final CouponIssueRepository couponIssueRepository;
  private final CouponIssueRequestService couponIssueRequestService;
  private final ConditionalCouponIssueProcessor conditionalCouponIssueProcessor;

  public CouponIssueResponse issue(Long couponEventId, Long userId) {
    return issue(couponEventId, userId, null);
  }

  public CouponIssueResponse issue(Long couponEventId, Long userId, String idempotencyKey) {
    CouponIssueRequestClaim claim = couponIssueRequestService.claim(couponEventId, userId, idempotencyKey);

    return switch (claim.type()) {
      case NEW -> executeNewRequest(claim.requestId(), couponEventId, userId);
      case SUCCESS_REPLAY -> reuseSuccess(claim.request());
      case FAILURE_REPLAY -> throw claim.request().getFailureReason().toBusinessException();
      case IN_PROGRESS_DUPLICATE -> throw new BusinessException(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
    };
  }

  private CouponIssueResponse executeNewRequest(Long requestId, Long couponEventId, Long userId) {
    try {
      return conditionalCouponIssueProcessor.issue(requestId, couponEventId, userId);
    } catch (BusinessException exception) {
      couponIssueRequestService.markFailed(requestId, CouponIssueFailureReason.from(exception.getErrorCode()));
      throw exception;
    } catch (Exception exception) {
      couponIssueRequestService.markFailed(requestId, CouponIssueFailureReason.INTERNAL_ERROR);
      throw exception;
    }
  }

  private CouponIssueResponse reuseSuccess(CouponIssueRequest request) {
    Long issueId = request.getIssuedCouponIssueId();
    CouponIssue couponIssue = couponIssueRepository.findById(issueId)
        .orElseThrow(() -> new IllegalStateException("기존 발급 결과를 찾을 수 없습니다. issueId=" + issueId));
    return CouponIssueResponse.reusedFrom(couponIssue);
  }
}
