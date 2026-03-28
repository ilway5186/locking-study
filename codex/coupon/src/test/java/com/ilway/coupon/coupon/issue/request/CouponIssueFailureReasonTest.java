package com.ilway.coupon.coupon.issue.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class CouponIssueFailureReasonTest {

  @Test
  void errorCode를_실패사유로_매핑할_수_있다() {
    assertThat(CouponIssueFailureReason.from(ErrorCode.COUPON_EVENT_NOT_FOUND)).isEqualTo(CouponIssueFailureReason.EVENT_NOT_FOUND);
    assertThat(CouponIssueFailureReason.from(ErrorCode.ISSUE_PERIOD_CLOSED)).isEqualTo(CouponIssueFailureReason.NOT_IN_ISSUE_PERIOD);
    assertThat(CouponIssueFailureReason.from(ErrorCode.ALREADY_ISSUED)).isEqualTo(CouponIssueFailureReason.ALREADY_ISSUED);
    assertThat(CouponIssueFailureReason.from(ErrorCode.SOLD_OUT)).isEqualTo(CouponIssueFailureReason.SOLD_OUT);
    assertThat(CouponIssueFailureReason.from(ErrorCode.CONFLICT_RETRY_EXCEEDED)).isEqualTo(CouponIssueFailureReason.CONFLICT_RETRY_EXCEEDED);
  }

  @Test
  void 실패사유는_같은_비즈니스예외로_복원할_수_있다() {
    BusinessException exception = CouponIssueFailureReason.SOLD_OUT.toBusinessException();

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SOLD_OUT);
    assertThat(exception.getMessage()).isEqualTo(ErrorCode.SOLD_OUT.getMessage());
  }
}
