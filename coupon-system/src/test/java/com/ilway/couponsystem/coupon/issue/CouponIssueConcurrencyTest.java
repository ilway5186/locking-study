package com.ilway.couponsystem.coupon.issue;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import com.ilway.couponsystem.coupon.event.CouponEventRepository;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestRepository;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestService;
import com.ilway.couponsystem.support.MySqlIntegrationTestSupport;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
public class CouponIssueConcurrencyTest extends MySqlIntegrationTestSupport {

  @Autowired CouponIssueService couponIssueService;
  @Autowired CouponEventRepository couponEventRepo;
  @Autowired CouponIssueRepository couponIssueRepo;
  @Autowired CouponIssueRequestRepository couponIssueRequestRepo;
  private static final String IDEMPOTENCY_KEY = "idempotency_key_123456";

  @BeforeEach
  void setUp() {
    couponIssueRepo.deleteAllInBatch();
    couponEventRepo.deleteAllInBatch();
    couponIssueRequestRepo.deleteAllInBatch();
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
      return () -> couponIssueService.issue(couponEvent.id(), (long) index+1, IDEMPOTENCY_KEY);
    };

    ConcurrentResult result = runConcurrent(1000, factory);
    CouponEvent reloaded = couponEventRepo.findById(couponEvent.id()).orElseThrow();

    assertThat(result.successCount()).isEqualTo(100);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(900);
    assertThat(couponIssueRepo.count()).isEqualTo(100);
    assertThat(reloaded.issuedQuantity()).isEqualTo(100);
  }

  @Test
  void 같은_멱등성키로_몇번을_요청하든_발급은_1번만_일어나고_나머지는_재사용_또는_중복요청처리된다() throws InterruptedException{
    LocalDateTime now = LocalDateTime.now();
    CouponEvent couponEvent = couponEventRepo.save(CouponEvent.create(
      "100명 선착순 이벤트",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    IndexedTaskFactory factory = (index) -> {
      return () -> couponIssueService.issue(couponEvent.id(), 1L, IDEMPOTENCY_KEY);
    };

    ConcurrentResult result = runConcurrent(100, factory);
    int reusedCount = couponIssueRequestRepo.findAll().getFirst().reusedCount();

    // 멱등성키가 1개이므로 발급된 쿠폰도 1개일거고
    // 쿠폰 생성 요청도 1개만 저장돼서 관리됨
    assertThat(couponIssueRepo.count()).isEqualTo(1);
    assertThat(couponIssueRequestRepo.count()).isEqualTo(1);
    assertThat(reusedCount).isEqualTo(99);
    assertThat(result.successCount() + result.failureCount(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS)).isEqualTo(100);

    log.info("성공: {}, DUPLICATE_REQUEST_IN_PROGRESS 실패: {}, ALREADY_ISSUED 실패: {}, SOLD_OUT 실패: {}",
      result.successCount(),
      result.failureCount(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS),
      result.failureCount(ErrorCode.ALREADY_ISSUED),
      result.failureCount(ErrorCode.SOLD_OUT)
    );
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
      return () -> couponIssueService.issue(couponEvent.id(), 1L, IDEMPOTENCY_KEY + "_" + index);
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
      () -> couponIssueService.issue(couponEvent.id(), (long) index + 1000, IDEMPOTENCY_KEY)
    );


    CouponEvent reloaded = couponEventRepo.findById(couponEvent.id()).orElseThrow();

    assertThat(result.successCount()).isEqualTo(30);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(270);
    assertThat(couponIssueRepo.count()).isEqualTo(30);
    assertThat(reloaded.issuedQuantity()).isEqualTo(30);
  }

  @Test
  void 고정스레드풀과_가상스레드의_한도초과_처리시간을_비교한다() throws InterruptedException {
    LocalDateTime now = LocalDateTime.now();
    CouponEvent fixedThreadPoolEvent = couponEventRepo.save(CouponEvent.create(
      "fixed-thread-pool-bulk",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    ConcurrentResult fixedThreadPoolResult = runConcurrent(
      1000,
      index -> () -> couponIssueService.issue(fixedThreadPoolEvent.id(), (long) index + 1, "fixed-thread-pool-" + index),
      ExecutionMode.FIXED_THREAD_POOL
    );
    long fixedThreadPoolIssuedCount = couponIssueRepo.countByCouponEvent_Id(fixedThreadPoolEvent.id());
    CouponEvent reloadedFixedThreadPoolEvent = couponEventRepo.findById(fixedThreadPoolEvent.id()).orElseThrow();

    couponIssueRequestRepo.deleteAllInBatch();
    couponIssueRepo.deleteAllInBatch();

    CouponEvent virtualThreadEvent = couponEventRepo.save(CouponEvent.create(
      "virtual-thread-bulk",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    ConcurrentResult virtualThreadResult = runConcurrent(
      1000,
      index -> () -> couponIssueService.issue(virtualThreadEvent.id(), (long) index + 1, "virtual-thread-" + index),
      ExecutionMode.VIRTUAL_THREAD
    );
    long virtualThreadIssuedCount = couponIssueRepo.countByCouponEvent_Id(virtualThreadEvent.id());
    CouponEvent reloadedVirtualThreadEvent = couponEventRepo.findById(virtualThreadEvent.id()).orElseThrow();

    assertBulkResult(fixedThreadPoolResult, fixedThreadPoolIssuedCount, reloadedFixedThreadPoolEvent);
    assertBulkResult(virtualThreadResult, virtualThreadIssuedCount, reloadedVirtualThreadEvent);

    log.info(
      "executor-bulk comparison fixedThreadPool={}ms success={} soldOut={} | virtualThread={}ms success={} soldOut={}",
      fixedThreadPoolResult.elapsedMillis(),
      fixedThreadPoolResult.successCount(),
      fixedThreadPoolResult.failureCount(ErrorCode.SOLD_OUT),
      virtualThreadResult.elapsedMillis(),
      virtualThreadResult.successCount(),
      virtualThreadResult.failureCount(ErrorCode.SOLD_OUT)
    );
  }

  @Test
  void 고정스레드풀과_가상스레드의_동일유저_처리시간을_비교한다() throws InterruptedException {
    LocalDateTime now = LocalDateTime.now();
    CouponEvent fixedThreadPoolEvent = couponEventRepo.save(CouponEvent.create(
      "fixed-thread-pool-same-user",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    ConcurrentResult fixedThreadPoolResult = runConcurrent(
      100,
      index -> () -> couponIssueService.issue(fixedThreadPoolEvent.id(), 1L, "fixed-thread-pool-same-user-" + index),
      ExecutionMode.FIXED_THREAD_POOL
    );
    long fixedThreadPoolIssuedCount = couponIssueRepo.countByCouponEvent_Id(fixedThreadPoolEvent.id());

    couponIssueRequestRepo.deleteAllInBatch();
    couponIssueRepo.deleteAllInBatch();

    CouponEvent virtualThreadEvent = couponEventRepo.save(CouponEvent.create(
      "virtual-thread-same-user",
      100,
      now.minusMinutes(10),
      now.plusMinutes(10))
    );

    ConcurrentResult virtualThreadResult = runConcurrent(
      100,
      index -> () -> couponIssueService.issue(virtualThreadEvent.id(), 1L, "virtual-thread-same-user-" + index),
      ExecutionMode.VIRTUAL_THREAD
    );
    long virtualThreadIssuedCount = couponIssueRepo.countByCouponEvent_Id(virtualThreadEvent.id());

    assertSameUserResult(fixedThreadPoolResult, fixedThreadPoolIssuedCount);
    assertSameUserResult(virtualThreadResult, virtualThreadIssuedCount);

    log.info(
      "executor-same-user comparison fixedThreadPool={}ms success={} alreadyIssued={} | virtualThread={}ms success={} alreadyIssued={}",
      fixedThreadPoolResult.elapsedMillis(),
      fixedThreadPoolResult.successCount(),
      fixedThreadPoolResult.failureCount(ErrorCode.ALREADY_ISSUED),
      virtualThreadResult.elapsedMillis(),
      virtualThreadResult.successCount(),
      virtualThreadResult.failureCount(ErrorCode.ALREADY_ISSUED)
    );
  }

  private ConcurrentResult runConcurrent(int totalRequests, IndexedTaskFactory taskFactory) throws InterruptedException {
    return runConcurrent(totalRequests, taskFactory, ExecutionMode.FIXED_THREAD_POOL);
  }

  private ConcurrentResult runConcurrent(int totalRequests, IndexedTaskFactory taskFactory, ExecutionMode executionMode) throws InterruptedException {
    return switch (executionMode) {
      case FIXED_THREAD_POOL -> runConcurrentWithFixedThreadPool(totalRequests, taskFactory);
      case VIRTUAL_THREAD -> runConcurrentWithVirtualThread(totalRequests, taskFactory);
    };
  }

  private ConcurrentResult runConcurrentWithFixedThreadPool(int totalRequests, IndexedTaskFactory taskFactory) throws InterruptedException {
    int poolSize = Math.min(totalRequests, 64);
    ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
    return runConcurrentInBatches(totalRequests, taskFactory, executorService, poolSize);
  }

  private ConcurrentResult runConcurrentWithVirtualThread(int totalRequests, IndexedTaskFactory taskFactory) throws InterruptedException {
    int poolSize = Math.min(totalRequests, 64);
    ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
    return runConcurrentInBatches(totalRequests, taskFactory, executorService, poolSize);
  }

  private ConcurrentResult runConcurrentInBatches(
    int totalRequests,
    IndexedTaskFactory taskFactory,
    ExecutorService executorService,
    int batchSizeLimit
  ) throws InterruptedException {
    AtomicInteger success = new AtomicInteger();
    Map<ErrorCode, AtomicInteger> failures = new ConcurrentHashMap<>();
    long startedAt = System.nanoTime();

    try {
      for (int from = 0; from < totalRequests; from += batchSizeLimit) {
        int batchSize = Math.min(batchSizeLimit, totalRequests - from);
        CountDownLatch ready = new CountDownLatch(batchSize);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(batchSize);

        for (int i = 0; i < batchSize; i++) {
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

    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    return new ConcurrentResult(elapsedMillis, success.get(), failures);
  }

  private void assertBulkResult(ConcurrentResult result, long issuedCount, CouponEvent couponEvent) {
    assertThat(result.successCount()).isEqualTo(100);
    assertThat(result.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(900);
    assertThat(result.failureCount(ErrorCode.INTERNAL_ERROR)).isZero();
    assertThat(issuedCount).isEqualTo(100);
    assertThat(couponEvent.issuedQuantity()).isEqualTo(100);
  }

  private void assertSameUserResult(ConcurrentResult result, long issuedCount) {
    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.failureCount(ErrorCode.ALREADY_ISSUED)).isEqualTo(99);
    assertThat(result.failureCount(ErrorCode.INTERNAL_ERROR)).isZero();
    assertThat(issuedCount).isEqualTo(1);
  }

  private record ConcurrentResult(long elapsedMillis, int successCount, Map<ErrorCode, AtomicInteger> failures) {

    public int failureCount(ErrorCode errorCode) {
      AtomicInteger count = failures.get(errorCode);
      return count == null ? 0 : count.get();
    }

  }

  private enum ExecutionMode {
    FIXED_THREAD_POOL,
    VIRTUAL_THREAD
  }

  @FunctionalInterface
  private interface IndexedTaskFactory {
    Runnable create(int index);
  }

}
