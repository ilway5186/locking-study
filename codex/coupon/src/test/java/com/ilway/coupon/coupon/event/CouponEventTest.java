package com.ilway.coupon.coupon.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CouponEventTest {

  @Test
  void 발급기간이면_true를_반환한다() {
    LocalDateTime now = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create(
        "선착순 이벤트",
        100,
        now.minusMinutes(10),
        now.plusMinutes(10)
    );

    assertThat(couponEvent.isIssuableAt(now)).isTrue();
  }

  @Test
  void 발급기간이_아니면_예외가_발생한다() {
    LocalDateTime now = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create(
        "선착순 이벤트",
        100,
        now.plusMinutes(1),
        now.plusMinutes(10)
    );

    assertThatThrownBy(() -> couponEvent.validateIssuableAt(now))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.ISSUE_PERIOD_CLOSED.getMessage());
  }

  @Test
  void 남은_수량을_정확히_계산한다() {
    LocalDateTime now = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create(
        "선착순 이벤트",
        2,
        now.minusMinutes(1),
        now.plusMinutes(1)
    );

    couponEvent.issueOne(now);

    assertThat(couponEvent.getIssuedQuantity()).isEqualTo(1);
    assertThat(couponEvent.remainingQuantity()).isEqualTo(1);
  }

  @Test
  void 수량이_모두_소진되면_예외가_발생한다() {
    LocalDateTime now = LocalDateTime.of(2026, 3, 26, 20, 0, 0);
    CouponEvent couponEvent = CouponEvent.create(
        "선착순 이벤트",
        1,
        now.minusMinutes(1),
        now.plusMinutes(1)
    );

    couponEvent.issueOne(now);

    assertThatThrownBy(() -> couponEvent.issueOne(now))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ErrorCode.SOLD_OUT.getMessage());
  }
}
