package com.ilway.coupon.comparison.conditional;

import static org.assertj.core.api.Assertions.assertThat;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.CouponEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ConditionalIssueFailureResolverTest {

  private final ConditionalIssueFailureResolver conditionalIssueFailureResolver = new ConditionalIssueFailureResolver();

  @Test
  void 최신상태가_기간밖이면_발급기간예외로_해석한다() {
    LocalDateTime now = LocalDateTime.of(2026, 3, 28, 12, 0, 0);
    CouponEvent couponEvent = CouponEvent.create("conditional", 10, now.plusMinutes(1), now.plusMinutes(10));

    BusinessException exception = conditionalIssueFailureResolver.resolve(couponEvent, now);

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ISSUE_PERIOD_CLOSED);
  }

  @Test
  void 최신상태가_소진이면_soldOut으로_해석한다() {
    LocalDateTime now = LocalDateTime.of(2026, 3, 28, 12, 0, 0);
    CouponEvent couponEvent = CouponEvent.create("conditional", 1, now.minusMinutes(1), now.plusMinutes(10));
    couponEvent.issueOne(now);

    BusinessException exception = conditionalIssueFailureResolver.resolve(couponEvent, now);

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SOLD_OUT);
  }
}
