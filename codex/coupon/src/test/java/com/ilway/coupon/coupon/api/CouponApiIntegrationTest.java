package com.ilway.coupon.coupon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.ilway.coupon.comparison.unsafe.UnsafeCouponIssueRepository;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.ilway.coupon.support.MySqlIntegrationTestSupport;

class CouponApiIntegrationTest extends MySqlIntegrationTestSupport {

  @Autowired
  private WebTestClient webTestClient;

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
  void 이벤트를_생성할_수_있다() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    String requestBody = """
        {
          "name": "오픈 기념 이벤트",
          "totalQuantity": 100,
          "startAt": "%s",
          "endAt": "%s"
        }
        """.formatted(now.minusMinutes(1), now.plusMinutes(10));

    webTestClient.post()
        .uri("/api/coupon-events")
        .contentType(APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.success").isEqualTo(true)
        .jsonPath("$.data.name").isEqualTo("오픈 기념 이벤트")
        .jsonPath("$.data.totalQuantity").isEqualTo(100);

    assertThat(couponEventRepository.count()).isEqualTo(1);
  }

  @Test
  void 정상적으로_쿠폰을_발급할_수_있다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "선착순 이벤트",
        10,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":1}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.success").isEqualTo(true)
        .jsonPath("$.data.couponEventId").isEqualTo(couponEvent.getId().intValue())
        .jsonPath("$.data.userId").isEqualTo(1)
        .jsonPath("$.data.resultType").isEqualTo("ISSUED");

    assertThat(couponIssueRepository.count()).isEqualTo(1);
  }

  @Test
  void 같은_idempotencyKey로_재요청하면_기존_성공결과를_재사용한다() {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "멱등 이벤트",
        10,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "issue-1")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":17}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.data.resultType").isEqualTo("ISSUED");

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "issue-1")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":17}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.success").isEqualTo(true)
        .jsonPath("$.data.resultType").isEqualTo("REUSED");

    assertThat(couponIssueRepository.count()).isEqualTo(1);
    assertThat(couponIssueRequestRepository.count()).isEqualTo(1);
  }

  @Test
  void 다른_idempotencyKey로_같은_유저가_재요청하면_중복발급에_실패한다() {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "중복 판별 이벤트",
        10,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "issue-a")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":21}")
        .exchange()
        .expectStatus().isOk();

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "issue-b")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":21}")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.success").isEqualTo(false)
        .jsonPath("$.error.code").isEqualTo("COUPON-ISSUE-410");

    assertThat(couponIssueRepository.count()).isEqualTo(1);
    assertThat(couponIssueRequestRepository.count()).isEqualTo(2);
  }

  @Test
  void 같은_유저는_중복_발급받을_수_없다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "선착순 이벤트",
        10,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    String requestBody = "{\"userId\":7}";

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isOk();

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.success").isEqualTo(false)
        .jsonPath("$.error.code").isEqualTo("COUPON-ISSUE-410");
  }

  @Test
  void 수량이_소진되면_발급에_실패한다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "선착순 이벤트",
        1,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":1}")
        .exchange()
        .expectStatus().isOk();

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":2}")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.success").isEqualTo(false)
        .jsonPath("$.error.code").isEqualTo("COUPON-ISSUE-409");
  }

  @Test
  void 유저별_발급이력을_조회할_수_있다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "선착순 이벤트",
        10,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":3}")
        .exchange()
        .expectStatus().isOk();

    webTestClient.get()
        .uri("/api/users/{userId}/coupon-issues", 3L)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.success").isEqualTo(true)
        .jsonPath("$.data[0].couponEventId").isEqualTo(couponEvent.getId().intValue())
        .jsonPath("$.data[0].eventName").isEqualTo("선착순 이벤트");
  }

  @Test
  void 관리자용_이벤트_통계를_조회할_수_있다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "통계 이벤트",
        5,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":11}")
        .exchange()
        .expectStatus().isOk();

    webTestClient.get()
        .uri("/api/admin/coupon-events/{couponEventId}/statistics", couponEvent.getId())
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.success").isEqualTo(true)
        .jsonPath("$.data.couponEventId").isEqualTo(couponEvent.getId().intValue())
        .jsonPath("$.data.successCount").isEqualTo(1)
        .jsonPath("$.data.totalRequestCount").isEqualTo(1)
        .jsonPath("$.data.totalAttemptCount").isEqualTo(1)
        .jsonPath("$.data.successRequestCount").isEqualTo(1)
        .jsonPath("$.data.failureRequestCount").isEqualTo(0)
        .jsonPath("$.data.reusedRequestCount").isEqualTo(0)
        .jsonPath("$.data.remainingQuantity").isEqualTo(4);
  }

  @Test
  void 요청이력기반으로_실패통계와_재사용횟수를_조회할_수_있다() {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "요청 이력 이벤트",
        1,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "success-1")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":31}")
        .exchange()
        .expectStatus().isOk();

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "success-1")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":31}")
        .exchange()
        .expectStatus().isOk();

    webTestClient.post()
        .uri("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
        .header("Idempotency-Key", "sold-out-1")
        .contentType(APPLICATION_JSON)
        .bodyValue("{\"userId\":32}")
        .exchange()
        .expectStatus().isEqualTo(409);

    webTestClient.get()
        .uri("/api/admin/coupon-events/{couponEventId}/statistics", couponEvent.getId())
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.data.totalRequestCount").isEqualTo(2)
        .jsonPath("$.data.totalAttemptCount").isEqualTo(3)
        .jsonPath("$.data.successRequestCount").isEqualTo(1)
        .jsonPath("$.data.failureRequestCount").isEqualTo(1)
        .jsonPath("$.data.reusedRequestCount").isEqualTo(1)
        .jsonPath("$.data.failureReasonCounts.SOLD_OUT").isEqualTo(1);
  }
}
