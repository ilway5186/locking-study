package com.ilway.coupon.comparison.optimistic;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class OptimisticRetryPolicy {

  private static final int MAX_ATTEMPTS = 3;

  public <T> T execute(RetryableSupplier<T> supplier) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return supplier.get();
      } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
        if (attempt == MAX_ATTEMPTS) {
          throw new BusinessException(ErrorCode.CONFLICT_RETRY_EXCEEDED);
        }
      }
    }
    throw new BusinessException(ErrorCode.CONFLICT_RETRY_EXCEEDED);
  }

  @FunctionalInterface
  public interface RetryableSupplier<T> {
    T get();
  }
}
