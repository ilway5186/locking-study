package com.ilway.coupon.coupon.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ilway.coupon.comparison.unsafe.UnsafeCouponIssueRepository;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ilway.coupon.support.MySqlIntegrationTestSupport;

class CouponApiIntegrationTest extends MySqlIntegrationTestSupport {

  private MockMvc mockMvc;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private CouponEventRepository couponEventRepository;

  @Autowired
  private CouponIssueRepository couponIssueRepository;

  @Autowired
  private UnsafeCouponIssueRepository unsafeCouponIssueRepository;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    unsafeCouponIssueRepository.deleteAllInBatch();
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

    mockMvc.perform(post("/api/coupon-events")
            .contentType(APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("오픈 기념 이벤트"))
        .andExpect(jsonPath("$.data.totalQuantity").value(100));

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

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content("{\"userId\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.couponEventId").value(couponEvent.getId()))
        .andExpect(jsonPath("$.data.userId").value(1));

    assertThat(couponIssueRepository.count()).isEqualTo(1);
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

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("COUPON-ISSUE-410"));
  }

  @Test
  void 수량이_소진되면_발급에_실패한다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "선착순 이벤트",
        1,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content("{\"userId\":1}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content("{\"userId\":2}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("COUPON-ISSUE-409"));
  }

  @Test
  void 유저별_발급이력을_조회할_수_있다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "선착순 이벤트",
        10,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content("{\"userId\":3}"))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/users/{userId}/coupon-issues", 3L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].couponEventId").value(couponEvent.getId()))
        .andExpect(jsonPath("$.data[0].eventName").value("선착순 이벤트"));
  }

  @Test
  void 관리자용_이벤트_통계를_조회할_수_있다() throws Exception {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "통계 이벤트",
        5,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    mockMvc.perform(post("/api/coupon-events/{couponEventId}/issues", couponEvent.getId())
            .contentType(APPLICATION_JSON)
            .content("{\"userId\":11}"))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/admin/coupon-events/{couponEventId}/statistics", couponEvent.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.couponEventId").value(couponEvent.getId()))
        .andExpect(jsonPath("$.data.successCount").value(1))
        .andExpect(jsonPath("$.data.remainingQuantity").value(4));
  }
}
