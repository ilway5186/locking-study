package com.ilway.coupon.comparison.optimistic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class OptimisticRetryPolicyTest {

  private final OptimisticRetryPolicy optimisticRetryPolicy = new OptimisticRetryPolicy();

  @Test
  void 충돌이_재시도한도_안에서_해결되면_성공한다() {
    AtomicInteger attempts = new AtomicInteger();

    String result = optimisticRetryPolicy.execute(() -> {
      if (attempts.getAndIncrement() < 2) {
        throw new ObjectOptimisticLockingFailureException("CouponEvent", 1L);
      }
      return "success";
    });

    assertThat(result).isEqualTo("success");
    assertThat(attempts.get()).isEqualTo(3);
  }

  @Test
  void 충돌이_계속되면_재시도한도초과_예외를_던진다() {
    assertThatThrownBy(() -> optimisticRetryPolicy.execute(() -> {
      throw new ObjectOptimisticLockingFailureException("CouponEvent", 1L);
    }))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONFLICT_RETRY_EXCEEDED);
  }
}
