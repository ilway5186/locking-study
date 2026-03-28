package com.ilway.coupon.coupon.issue.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CouponIssueRequestTest {

  @Test
  void 요청이_성공하면_success상태와_issueId를_기록한다() {
    CouponIssueRequest request = CouponIssueRequest.createInProgress("idem-1", 1L, 2L);

    request.markSuccess(99L);

    assertThat(request.getRequestStatus()).isEqualTo(CouponIssueRequestStatus.SUCCESS);
    assertThat(request.getIssuedCouponIssueId()).isEqualTo(99L);
    assertThat(request.getFailureReason()).isNull();
  }

  @Test
  void 요청이_실패하면_failed상태와_실패사유를_기록한다() {
    CouponIssueRequest request = CouponIssueRequest.createInProgress("idem-2", 1L, 2L);

    request.markFailed(CouponIssueFailureReason.SOLD_OUT);

    assertThat(request.getRequestStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
    assertThat(request.getFailureReason()).isEqualTo(CouponIssueFailureReason.SOLD_OUT);
    assertThat(request.getIssuedCouponIssueId()).isNull();
  }

  @Test
  void 같은_요청키_재사용이_들어오면_reusedCount가_증가한다() {
    CouponIssueRequest request = CouponIssueRequest.createInProgress("idem-3", 1L, 2L);

    request.markReused();
    request.markReused();

    assertThat(request.getReusedCount()).isEqualTo(2);
  }
}
