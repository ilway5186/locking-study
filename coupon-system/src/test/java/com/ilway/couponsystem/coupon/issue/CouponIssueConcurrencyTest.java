package com.ilway.couponsystem.coupon.issue;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import com.ilway.couponsystem.coupon.event.CouponEventRepository;
import com.ilway.couponsystem.support.MySqlIntegrationTestSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.*;

public class CouponIssueConcurrencyTest extends MySqlIntegrationTestSupport {

  @Autowired CouponIssueService couponIssueService;
  @Autowired CouponEventRepository couponEventRepo;
  @Autowired CouponIssueRepository couponIssueRepo;

  @BeforeEach
  void setUp() {
    couponIssueRepo.deleteAllInBatch();
    couponEventRepo.deleteAllInBatch();
  }

  @Test
  void 한도초과_요청이와도_정해진_수량만큼만_발급한다() throws InterruptedException {
    LocalDateTime now = LocalDateTime.now();
    CouponEvent couponEvent = couponEventRepo.save(CouponEvent.create(
      "100명 선착순 이벤트",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    IndexedTaskFactory factory = (index) -> {
      return () -> couponIssueService.issue(couponEvent.id(), (long) index+1);
    };

    ConcurrentResult result = runConcurrent(1000, factory);
    CouponEvent reloaded = couponEventRepo.findById(couponEvent.id()).orElseThrow();

    assertThat(result.successCount()).isEqualTo(100);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(900);
    assertThat(couponIssueRepo.count()).isEqualTo(100);
    assertThat(reloaded.issuedQuantity()).isEqualTo(100);
  }

  @Test
  void 동일유저가_몇번을_요청하든_1개만_성공한다() throws InterruptedException {
    LocalDateTime now = LocalDateTime.now();
    CouponEvent couponEvent = couponEventRepo.save(CouponEvent.create(
      "100명 선착순 이벤트",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    IndexedTaskFactory factory = (index) -> {
      return () -> couponIssueService.issue(couponEvent.id(), 1L);
    };

    ConcurrentResult result = runConcurrent(100, factory);

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.failureCount(ErrorCode.ALREADY_ISSUED)).isEqualTo(99);
    assertThat(couponIssueRepo.countByCouponEvent_Id(couponEvent.id())).isEqualTo(1);
  }

  @Test
  void 동시요청이_들어와도_총_발급량을_초과하지_않는다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepo.save(CouponEvent.create(
      "safe-bulk",
      30,
      LocalDateTime.now().minusMinutes(1),
      LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult result = runConcurrent(300, index ->
      () -> couponIssueService.issue(couponEvent.id(),
        (long) index + 1000));

    CouponEvent reloaded = couponEventRepo.findById(couponEvent.id()).orElseThrow();

    assertThat(result.successCount()).isEqualTo(30);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(270);
    assertThat(couponIssueRepo.count()).isEqualTo(30);
    assertThat(reloaded.issuedQuantity()).isEqualTo(30);
  }

  private ConcurrentResult runConcurrent(int totalRequests, IndexedTaskFactory taskFactory) throws InterruptedException {
    int poolSize = Math.min(totalRequests, 64);
    ExecutorService executorService = Executors.newFixedThreadPool(poolSize);

    AtomicInteger success = new AtomicInteger();
    Map<ErrorCode, AtomicInteger> failures = new ConcurrentHashMap<>();

    try {
      for (int from = 0; from < totalRequests; from+=poolSize) {
        int batchSize = Math.min(poolSize, totalRequests - from);
        CountDownLatch ready = new CountDownLatch(batchSize);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(batchSize);

        for (int i=0; i<batchSize; i++) {
          int index = from + i;
          executorService.submit(() -> {
            ready.countDown();
            try {
              start.await();
              taskFactory.create(index).run();
              success.incrementAndGet();
            } catch (BusinessException e) {
              failures.computeIfAbsent(e.errorCode(), ignore -> new AtomicInteger())
                .incrementAndGet();
            } catch (Exception e) {
              failures.computeIfAbsent(ErrorCode.INTERNAL_ERROR, ignore -> new AtomicInteger())
                .incrementAndGet();
            } finally {
              done.countDown();
            }
          });
        }

        assertThat(ready.await(10, SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, SECONDS)).isTrue();
      }
    } finally {
      executorService.shutdown();
      assertThat(executorService.awaitTermination(10, SECONDS)).isTrue();
    }

    return new ConcurrentResult(success.get(), failures);
  }

  private record ConcurrentResult(int successCount, Map<ErrorCode, AtomicInteger> failures) {

    public int failureCount(ErrorCode errorCode) {
      AtomicInteger count = failures.get(errorCode);
      return count == null ? 0 : count.get();
    }

  }

  @FunctionalInterface
  private interface IndexedTaskFactory {
    Runnable create(int index);
  }

}
