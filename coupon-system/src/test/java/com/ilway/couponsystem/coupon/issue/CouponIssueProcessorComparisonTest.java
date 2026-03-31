package com.ilway.couponsystem.coupon.issue;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import com.ilway.couponsystem.coupon.event.CouponEventRepository;
import com.ilway.couponsystem.coupon.issue.api.CouponIssueResponse;
import com.ilway.couponsystem.coupon.issue.processor.ConditionalCouponIssueProcessor;
import com.ilway.couponsystem.coupon.issue.processor.CouponIssueProcessor;
import com.ilway.couponsystem.coupon.issue.processor.PessimisticCouponIssueProcessor;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueFailureReason;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequest;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestRepository;
import com.ilway.couponsystem.coupon.issue.request.CouponIssueRequestService;
import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistration;
import com.ilway.couponsystem.support.MySqlIntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class CouponIssueProcessorComparisonTest extends MySqlIntegrationTestSupport {

  private static final int BULK_TOTAL_QUANTITY = 100;
  private static final int BULK_TOTAL_REQUESTS = 1000;
  private static final int SAME_USER_TOTAL_REQUESTS = 100;

  @Autowired ConditionalCouponIssueProcessor conditionalCouponIssueProcessor;
  @Autowired PessimisticCouponIssueProcessor pessimisticCouponIssueProcessor;
  @Autowired CouponEventRepository couponEventRepository;
  @Autowired CouponIssueRepository couponIssueRepository;
  @Autowired CouponIssueRequestRepository couponIssueRequestRepository;
  @Autowired CouponIssueRequestService couponIssueRequestService;

  @BeforeEach
  void setUp() {
    couponIssueRequestRepository.deleteAllInBatch();
    couponIssueRepository.deleteAllInBatch();
    couponEventRepository.deleteAllInBatch();
  }

  @Test
  void 조건부update와_비관적락은_같은_재고경합에서_둘다_정확히_수량만큼만_발급한다() throws InterruptedException {
    CouponEvent conditionalEvent = createEvent("conditional-bulk", BULK_TOTAL_QUANTITY);
    ComparisonRun conditionalRun = runComparison(
      "conditional-bulk",
      conditionalCouponIssueProcessor,
      conditionalEvent.id(),
      BULK_TOTAL_REQUESTS,
      index -> (long) index + 1,
      index -> "conditional-bulk-" + index
    );

    long conditionalIssued = couponIssueRepository.countByCouponEvent_Id(conditionalEvent.id());
    CouponEvent reloadedConditionalEvent = couponEventRepository.findById(conditionalEvent.id()).orElseThrow();

    couponIssueRequestRepository.deleteAllInBatch();
    couponIssueRepository.deleteAllInBatch();

    CouponEvent pessimisticEvent = createEvent("pessimistic-bulk", BULK_TOTAL_QUANTITY);
    ComparisonRun pessimisticRun = runComparison(
      "pessimistic-bulk",
      pessimisticCouponIssueProcessor,
      pessimisticEvent.id(),
      BULK_TOTAL_REQUESTS,
      index -> (long) index + 1,
      index -> "pessimistic-bulk-" + index
    );

    long pessimisticIssued = couponIssueRepository.countByCouponEvent_Id(pessimisticEvent.id());
    CouponEvent reloadedPessimisticEvent = couponEventRepository.findById(pessimisticEvent.id()).orElseThrow();

    assertBulkRun(conditionalRun, conditionalIssued, reloadedConditionalEvent.issuedQuantity());
    assertBulkRun(pessimisticRun, pessimisticIssued, reloadedPessimisticEvent.issuedQuantity());

    log.info(
      "bulk comparison conditional={}ms success={} soldOut={} | pessimistic={}ms success={} soldOut={}",
      conditionalRun.elapsedMillis(),
      conditionalRun.successCount(),
      conditionalRun.failureCount(ErrorCode.SOLD_OUT),
      pessimisticRun.elapsedMillis(),
      pessimisticRun.successCount(),
      pessimisticRun.failureCount(ErrorCode.SOLD_OUT)
    );
  }

  @Test
  void 조건부update와_비관적락은_같은유저_동시요청에서도_하나만_성공한다() throws InterruptedException {
    CouponEvent conditionalEvent = createEvent("conditional-same-user", SAME_USER_TOTAL_REQUESTS);
    ComparisonRun conditionalRun = runComparison(
      "conditional-same-user",
      conditionalCouponIssueProcessor,
      conditionalEvent.id(),
      SAME_USER_TOTAL_REQUESTS,
      index -> 7L,
      index -> "conditional-same-user-" + index
    );

    long conditionalIssued = couponIssueRepository.countByCouponEvent_Id(conditionalEvent.id());

    couponIssueRequestRepository.deleteAllInBatch();
    couponIssueRepository.deleteAllInBatch();

    CouponEvent pessimisticEvent = createEvent("pessimistic-same-user", SAME_USER_TOTAL_REQUESTS);
    ComparisonRun pessimisticRun = runComparison(
      "pessimistic-same-user",
      pessimisticCouponIssueProcessor,
      pessimisticEvent.id(),
      SAME_USER_TOTAL_REQUESTS,
      index -> 7L,
      index -> "pessimistic-same-user-" + index
    );

    long pessimisticIssued = couponIssueRepository.countByCouponEvent_Id(pessimisticEvent.id());

    assertSameUserRun(conditionalRun, conditionalIssued);
    assertSameUserRun(pessimisticRun, pessimisticIssued);

    log.info(
      "same-user comparison conditional={}ms success={} alreadyIssued={} | pessimistic={}ms success={} alreadyIssued={}",
      conditionalRun.elapsedMillis(),
      conditionalRun.successCount(),
      conditionalRun.failureCount(ErrorCode.ALREADY_ISSUED),
      pessimisticRun.elapsedMillis(),
      pessimisticRun.successCount(),
      pessimisticRun.failureCount(ErrorCode.ALREADY_ISSUED)
    );
  }

  private CouponEvent createEvent(String name, int totalQuantity) {
    return couponEventRepository.save(CouponEvent.create(
      name,
      totalQuantity,
      LocalDateTime.now().minusMinutes(1),
      LocalDateTime.now().plusMinutes(10)
    ));
  }

  private void assertBulkRun(ComparisonRun run, long issuedCount, int issuedQuantity) {
    assertThat(run.successCount()).isEqualTo(BULK_TOTAL_QUANTITY);
    assertThat(run.failureCount(ErrorCode.SOLD_OUT)).isEqualTo(BULK_TOTAL_REQUESTS - BULK_TOTAL_QUANTITY);
    assertThat(run.failureCount(ErrorCode.INTERNAL_ERROR)).isZero();
    assertThat(issuedCount).isEqualTo(BULK_TOTAL_QUANTITY);
    assertThat(issuedQuantity).isEqualTo(BULK_TOTAL_QUANTITY);
  }

  private void assertSameUserRun(ComparisonRun run, long issuedCount) {
    assertThat(run.successCount()).isEqualTo(1);
    assertThat(run.failureCount(ErrorCode.ALREADY_ISSUED)).isEqualTo(SAME_USER_TOTAL_REQUESTS - 1);
    assertThat(run.failureCount(ErrorCode.INTERNAL_ERROR)).isZero();
    assertThat(issuedCount).isEqualTo(1);
  }

  private ComparisonRun runComparison(
    String label,
    CouponIssueProcessor processor,
    Long couponEventId,
    int totalRequests,
    UserIdFactory userIdFactory,
    IdempotencyKeyFactory idempotencyKeyFactory
  ) throws InterruptedException {
    int poolSize = Math.min(totalRequests, 64);
    ExecutorService executorService = Executors.newFixedThreadPool(poolSize);
    AtomicInteger success = new AtomicInteger();
    Map<ErrorCode, AtomicInteger> failures = new ConcurrentHashMap<>();

    long startedAt = System.nanoTime();
    try {
      for (int from = 0; from < totalRequests; from += poolSize) {
        int batchSize = Math.min(poolSize, totalRequests - from);
        CountDownLatch ready = new CountDownLatch(batchSize);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(batchSize);

        for (int i = 0; i < batchSize; i++) {
          int index = from + i;
          executorService.submit(() -> {
            ready.countDown();
            try {
              start.await();
              issueWithProcessor(
                processor,
                couponEventId,
                userIdFactory.create(index),
                idempotencyKeyFactory.create(index)
              );
              success.incrementAndGet();
            } catch (BusinessException exception) {
              failures.computeIfAbsent(exception.errorCode(), ignored -> new AtomicInteger())
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
      }
    } finally {
      executorService.shutdown();
      assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

    log.info(
      "{} elapsed={}ms success={} soldOut={} alreadyIssued={} duplicateInProgress={} internalError={}",
      label,
      elapsedMillis,
      success.get(),
      getFailureCount(failures, ErrorCode.SOLD_OUT),
      getFailureCount(failures, ErrorCode.ALREADY_ISSUED),
      getFailureCount(failures, ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS),
      getFailureCount(failures, ErrorCode.INTERNAL_ERROR)
    );

    return new ComparisonRun(elapsedMillis, success.get(), failures);
  }

  private CouponIssueResponse issueWithProcessor(
    CouponIssueProcessor processor,
    Long couponEventId,
    Long userId,
    String idempotencyKey
  ) {
    CouponIssueRequestRegistration registration = couponIssueRequestService.register(couponEventId, userId, idempotencyKey);

    return switch (registration.type()) {
      case NEW -> executeNewRequest(processor, registration.request().id(), couponEventId, userId);
      case SUCCESS_REPLAY -> reuseSuccess(registration.request());
      case FAILURE_REPLAY -> throw registration.request().failureReason().toBusinessException();
      case IN_PROGRESS_DUPLICATE -> throw new BusinessException(ErrorCode.DUPLICATE_REQUEST_IN_PROGRESS);
    };
  }

  private CouponIssueResponse executeNewRequest(
    CouponIssueProcessor processor,
    Long requestId,
    Long couponEventId,
    Long userId
  ) {
    try {
      return processor.issue(requestId, couponEventId, userId);
    } catch (BusinessException exception) {
      couponIssueRequestService.markFailed(requestId, CouponIssueFailureReason.from(exception.errorCode()));
      throw exception;
    } catch (Exception exception) {
      couponIssueRequestService.markFailed(requestId, CouponIssueFailureReason.INTERNAL_ERROR);
      throw exception;
    }
  }

  private CouponIssueResponse reuseSuccess(CouponIssueRequest request) {
    CouponIssue couponIssue = couponIssueRepository.findById(request.issuedCouponIssueId())
      .orElseThrow(() -> new IllegalStateException("기존 발급 결과를 찾을 수 없습니다. issueId=" + request.issuedCouponIssueId()));

    return CouponIssueResponse.reusedFrom(couponIssue);
  }

  private int getFailureCount(Map<ErrorCode, AtomicInteger> failures, ErrorCode errorCode) {
    AtomicInteger count = failures.get(errorCode);
    return count == null ? 0 : count.get();
  }

  @FunctionalInterface
  private interface UserIdFactory {
    Long create(int index);
  }

  @FunctionalInterface
  private interface IdempotencyKeyFactory {
    String create(int index);
  }

  private record ComparisonRun(long elapsedMillis, int successCount, Map<ErrorCode, AtomicInteger> failures) {

    int failureCount(ErrorCode errorCode) {
      AtomicInteger count = failures.get(errorCode);
      return count == null ? 0 : count.get();
    }
  }
}
