package com.ilway.coupon.coupon.issue;

import static org.assertj.core.api.Assertions.assertThat;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.comparison.conditional.ConditionalCouponIssueService;
import com.ilway.coupon.comparison.optimistic.OptimisticCouponIssueService;
import com.ilway.coupon.comparison.unsafe.UnsafeCouponIssueRepository;
import com.ilway.coupon.comparison.unsafe.UnsafeCouponIssueService;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestRepository;
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
  private ConditionalCouponIssueService conditionalCouponIssueService;

  @Autowired
  private OptimisticCouponIssueService optimisticCouponIssueService;

  @Autowired
  private CouponEventRepository couponEventRepository;

  @Autowired
  private CouponIssueRepository couponIssueRepository;

  @Autowired
  private UnsafeCouponIssueRepository unsafeCouponIssueRepository;

  @Autowired
  private CouponIssueRequestRepository couponIssueRequestRepository;

  @BeforeEach
  void setUp() {
    unsafeCouponIssueRepository.deleteAllInBatch();
    couponIssueRequestRepository.deleteAllInBatch();
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
  void 같은_idempotencyKey로_100번_동시에_요청하면_발급은_1번만_일어나고_나머지는_재사용또는_중복요청처리된다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "safe-idempotency",
        100,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult result = runConcurrent(100, index -> () -> couponIssueService.issue(couponEvent.getId(), 77L, "same-key"));

    long reusedCount = couponIssueRequestRepository.findAll().getFirst().getReusedCount();

    assertThat(couponIssueRepository.count()).isEqualTo(1);
    assertThat(couponIssueRequestRepository.count()).isEqualTo(1);
    assertThat(reusedCount).isEqualTo(99);
    assertThat(result.successCount() + result.failureCount(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS)).isEqualTo(100);
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
  void 조건부update구현도_동시요청에서_총_발급량을_초과하지_않는다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "conditional-bulk",
        30,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    long startedAt = System.nanoTime();
    ConcurrentResult result = runConcurrent(300, index -> () -> conditionalCouponIssueService.issue(couponEvent.getId(), (long) index + 5000));
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    CouponEvent reloaded = couponEventRepository.findById(couponEvent.getId()).orElseThrow();

    System.out.printf("conditional-update elapsed=%dms success=%d soldOut=%d%n",
        elapsedMillis,
        result.successCount(),
        result.failureCount(ErrorCode.SOLD_OUT)
    );

    assertThat(result.successCount()).isEqualTo(30);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(270);
    assertThat(couponIssueRepository.count()).isEqualTo(30);
    assertThat(reloaded.getIssuedQuantity()).isEqualTo(30);
  }

  @Test
  void 비관적락과_조건부update는_둘다_안전하지만_흐름이_다르다() throws InterruptedException {
    CouponEvent pessimisticEvent = couponEventRepository.save(CouponEvent.create(
        "pessimistic-compare",
        50,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));
    CouponEvent conditionalEvent = couponEventRepository.save(CouponEvent.create(0
        "conditional-compare",
        50,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult pessimisticResult = runConcurrent(
        500,
        index -> () -> couponIssueService.issue(pessimisticEvent.getId(), (long) index + 10000)
    );
    couponIssueRepository.deleteAllInBatch();
    couponIssueRequestRepository.deleteAllInBatch();

    ConcurrentResult conditionalResult = runConcurrent(
        500,
        index -> () -> conditionalCouponIssueService.issue(conditionalEvent.getId(), (long) index + 20000)
    );

    CouponEvent pessimisticReloaded = couponEventRepository.findById(pessimisticEvent.getId()).orElseThrow();
    CouponEvent conditionalReloaded = couponEventRepository.findById(conditionalEvent.getId()).orElseThrow();

    assertThat(pessimisticResult.successCount()).isEqualTo(50);
    assertThat(conditionalResult.successCount()).isEqualTo(50);
    assertThat(pessimisticReloaded.getIssuedQuantity()).isEqualTo(50);
    assertThat(conditionalReloaded.getIssuedQuantity()).isEqualTo(50);
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

  @Test
  void 낙관적락구현은_충돌이_발생하면_재시도하고_일부는_재시도초과로_실패할_수_있다() throws InterruptedException {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "optimistic-conflict",
        200,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    ConcurrentResult result = runConcurrent(200, index -> () -> optimisticCouponIssueService.issue(couponEvent.getId(), (long) index + 30000));
    CouponEvent reloaded = couponEventRepository.findById(couponEvent.getId()).orElseThrow();

    assertThat(result.failureCount(ErrorCode.CONFLICT_RETRY_EXCEEDED)).isGreaterThan(0);
    assertThat(result.successCount()).isLessThan(200);
    assertThat(reloaded.getIssuedQuantity()).isEqualTo((int) couponIssueRepository.count());
    assertThat(reloaded.getIssuedQuantity()).isLessThanOrEqualTo(200);
  }

  private ConcurrentResult runConcurrent(int totalRequests, IndexedTaskFactory taskFactory) throws InterruptedException {
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
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

    assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();

    executorService.shutdown();
    assertThat(executorService.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

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
