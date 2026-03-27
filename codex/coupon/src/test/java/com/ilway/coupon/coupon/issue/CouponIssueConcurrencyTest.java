package com.ilway.coupon.coupon.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.comparison.unsafe.UnsafeCouponIssueRepository;
import com.ilway.coupon.comparison.unsafe.UnsafeCouponIssueService;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.support.MySqlIntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CouponIssueConcurrencyTest extends MySqlIntegrationTestSupport {

  @Autowired
  private CouponIssueService couponIssueService;

  @Autowired
  private UnsafeCouponIssueService unsafeCouponIssueService;

  @Autowired
  private CouponEventRepository couponEventRepository;

  @Autowired
  private CouponIssueRepository couponIssueRepository;

  @Autowired
  private UnsafeCouponIssueRepository unsafeCouponIssueRepository;

  @BeforeEach
  void setUp() {
    unsafeCouponIssueRepository.deleteAllInBatch();
    couponIssueRepository.deleteAllInBatch();
    couponEventRepository.deleteAllInBatch();
  }

  @Test
  void safe구현은_수량100에_동시요청1000개가_와도_정확히100개만_성공한다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "safe-100",
        100,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult result = runConcurrent(1000, index -> () -> couponIssueService.issue(couponEvent.getId(), (long) index + 1));

    CouponEvent reloaded = couponEventRepository.findById(couponEvent.getId()).orElseThrow();

    assertThat(result.successCount()).isEqualTo(100);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(900);
    assertThat(couponIssueRepository.count()).isEqualTo(100);
    assertThat(reloaded.getIssuedQuantity()).isEqualTo(100);
  }

  @Test
  void safe구현은_같은유저가_100번_동시에_요청해도_1개만_성공한다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "safe-duplicate",
        100,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult result = runConcurrent(100, index -> () -> couponIssueService.issue(couponEvent.getId(), 99L));

    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.failureCount(ErrorCode.ALREADY_ISSUED)).isEqualTo(99);
    assertThat(couponIssueRepository.count()).isEqualTo(1);
  }

  @Test
  void safe구현은_여러_유저가_동시에_요청해도_총_발급량을_초과하지_않는다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "safe-bulk",
        30,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult result = runConcurrent(300, index -> () -> couponIssueService.issue(couponEvent.getId(), (long) index + 1000));

    CouponEvent reloaded = couponEventRepository.findById(couponEvent.getId()).orElseThrow();

    assertThat(result.successCount()).isEqualTo(30);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(270);
    assertThat(couponIssueRepository.count()).isEqualTo(30);
    assertThat(reloaded.getIssuedQuantity()).isEqualTo(30);
  }

  @Test
  void unsafe구현은_일반조회와_트랜잭션만으로는_초과발급이_발생할_수_있다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "unsafe-oversell",
        100,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    runConcurrent(1000, index -> () -> unsafeCouponIssueService.issue(couponEvent.getId(), (long) index + 1));

    long unsafeIssueCount = unsafeCouponIssueRepository.countByCouponEventId(couponEvent.getId());

    assertThat(unsafeIssueCount).isGreaterThan(100);
  }

  @Test
  void unsafe구현은_sameUser_동시요청에서_중복발급이_발생할_수_있다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "unsafe-duplicate",
        1000,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    runConcurrent(100, index -> () -> unsafeCouponIssueService.issue(couponEvent.getId(), 7L));

    long duplicateCount = unsafeCouponIssueRepository.countByCouponEventId(couponEvent.getId());

    assertThat(duplicateCount).isGreaterThan(1);
  }

  private ConcurrentResult runConcurrent(int totalRequests, IndexedTaskFactory taskFactory) throws InterruptedException {
    int poolSize = Math.min(totalRequests, 64);
    ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
    CountDownLatch ready = new CountDownLatch(totalRequests);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(totalRequests);
    AtomicInteger success = new AtomicInteger();
    Map<ErrorCode, AtomicInteger> failures = new ConcurrentHashMap<>();

    for (int index = 0; index < totalRequests; index++) {
      int currentIndex = index;
      executorService.submit(() -> {
        ready.countDown();
        try {
          start.await();
          taskFactory.create(currentIndex).run();
          success.incrementAndGet();
        } catch (BusinessException exception) {
          failures.computeIfAbsent(exception.getErrorCode(), ignored -> new AtomicInteger())
              .incrementAndGet();
        } catch (Exception exception) {
          failures.computeIfAbsent(ErrorCode.INTERNAL_ERROR, ignored -> new AtomicInteger())
              .incrementAndGet();
        } finally {
          done.countDown();
        }
      });
    }

    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();

    executorService.shutdown();
    assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

    return new ConcurrentResult(success.get(), failures);
  }

  @FunctionalInterface
  private interface IndexedTaskFactory {
    Runnable create(int index);
  }

  private record ConcurrentResult(int successCount, Map<ErrorCode, AtomicInteger> failures) {
    int failureCount(ErrorCode errorCode) {
      AtomicInteger count = failures.get(errorCode);
      return count == null ? 0 : count.get();
    }
  }
}
