package com.ilway.coupon.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.comparison.conditional.ConditionalCouponIssueService;
import com.ilway.coupon.comparison.optimistic.OptimisticCouponIssueService;
import com.ilway.coupon.coupon.event.CouponEvent;
import com.ilway.coupon.coupon.event.CouponEventRepository;
import com.ilway.coupon.coupon.issue.CouponIssueRepository;
import com.ilway.coupon.coupon.issue.request.CouponIssueRequestRepository;
import com.ilway.coupon.support.MySqlIntegrationTestSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ComparisonCouponIssueIntegrationTest extends MySqlIntegrationTestSupport {

  @Autowired
  private ConditionalCouponIssueService conditionalCouponIssueService;

  @Autowired
  private OptimisticCouponIssueService optimisticCouponIssueService;

  @Autowired
  private CouponEventRepository couponEventRepository;

  @Autowired
  private CouponIssueRepository couponIssueRepository;

  @Autowired
  private CouponIssueRequestRepository couponIssueRequestRepository;

  @BeforeEach
  void setUp() {
    couponIssueRequestRepository.deleteAllInBatch();
    couponIssueRepository.deleteAllInBatch();
    couponEventRepository.deleteAllInBatch();
  }

  @Test
  void 조건부update버전도_정상발급할_수_있다() {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "conditional",
        2,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    conditionalCouponIssueService.issue(couponEvent.getId(), 1L, "conditional-1");

    assertThat(couponIssueRepository.count()).isEqualTo(1);
    assertThat(couponIssueRequestRepository.count()).isEqualTo(1);
  }

  @Test
  void 낙관적락버전도_같은유저중복은_고유키로_막는다() {
    CouponEvent couponEvent = couponEventRepository.save(CouponEvent.create(
        "optimistic",
        2,
        LocalDateTime.now().minusMinutes(1),
        LocalDateTime.now().plusMinutes(10)
    ));

    optimisticCouponIssueService.issue(couponEvent.getId(), 7L, "optimistic-1");

    assertThatThrownBy(() -> optimisticCouponIssueService.issue(couponEvent.getId(), 7L, "optimistic-2"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ALREADY_ISSUED);
  }
}
