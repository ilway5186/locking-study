package com.ilway.couponsystem.coupon.event;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

public class CouponEventTest {

  @Test
  void 발급기간이면_true를_반환한다() {
    LocalDateTime time = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create("선착순 이벤트", 100, time.minusMinutes(10), time.plusMinutes(10));

    assertThat(couponEvent.isIssuableAt(time)).isTrue();
  }

  @Test
  void 발급기간이_아니면_예외가_발생한다() {
    LocalDateTime time = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create("선착순 이벤트", 100, time.plusMinutes(10), time.plusMinutes(20));

    assertThatThrownBy(() -> couponEvent.validateIssuableAt(time))
      .isInstanceOf(BusinessException.class)
      .hasMessage(ErrorCode.ISSUE_PERIOD_CLOSED.message());
  }

  @Test
  void 남은_수량을_정확히_계산한다() {
    LocalDateTime time = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create("선착순 이벤트", 100, time.minusMinutes(10), time.plusMinutes(20));

    couponEvent.issueOne(time);

    assertThat(couponEvent.remainingQuantity()).isEqualTo(99);
    assertThat(couponEvent.issuedQuantity()).isEqualTo(1);
  }

  @Test
  void 수량이_모두_소진되면_예외가_발생한다() {
    LocalDateTime time = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create("선착순 이벤트", 1, time.minusMinutes(10), time.plusMinutes(20));

    couponEvent.issueOne(time);

    assertThatThrownBy(() -> couponEvent.issueOne(time))
      .isInstanceOf(BusinessException.class)
      .hasMessage(ErrorCode.SOLD_OUT.message());
  }

}
