package com.ilway.coupon.coupon.issue.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CouponIssueRequestServiceTest {

  @Mock
  private CouponIssueRequestRepository couponIssueRequestRepository;

  @Mock
  private CouponIssueRequestRegistrationWriter couponIssueRequestRegistrationWriter;

  @InjectMocks
  private CouponIssueRequestService couponIssueRequestService;

  @Test
  void 새로운_idempotencyKey면_새로운_요청을_register한다() {
    CouponIssueRequest saved = CouponIssueRequest.createInProgress("idem-1", 1L, 2L);
    given(couponIssueRequestRegistrationWriter.createInProgress("idem-1", 1L, 2L)).willReturn(saved);

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-1");

    assertThat(registration.type()).isEqualTo(CouponIssueRequestRegistrationType.NEW);
    verify(couponIssueRequestRegistrationWriter).createInProgress("idem-1", 1L, 2L);
  }

  @Test
  void 성공했던_요청키가_다시_오면_성공결과를_재사용한다() {
    CouponIssueRequest existing = CouponIssueRequest.createInProgress("idem-2", 1L, 2L);
    existing.markSuccess(10L);

    given(couponIssueRequestRegistrationWriter.createInProgress("idem-2", 1L, 2L))
        .willThrow(new DataIntegrityViolationException("duplicate"));
    given(couponIssueRequestRepository.findByCouponEventIdAndUserIdAndIdempotencyKey(1L, 2L, "idem-2"))
        .willReturn(Optional.of(existing));

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-2");

    assertThat(registration.type()).isEqualTo(CouponIssueRequestRegistrationType.SUCCESS_REPLAY);
    assertThat(existing.getReusedCount()).isEqualTo(1);
  }

  @Test
  void 실패했던_요청키가_다시_오면_같은_실패를_재사용한다() {
    CouponIssueRequest existing = CouponIssueRequest.createInProgress("idem-3", 1L, 2L);
    existing.markFailed(CouponIssueFailureReason.SOLD_OUT);

    given(couponIssueRequestRegistrationWriter.createInProgress("idem-3", 1L, 2L))
        .willThrow(new DataIntegrityViolationException("duplicate"));
    given(couponIssueRequestRepository.findByCouponEventIdAndUserIdAndIdempotencyKey(1L, 2L, "idem-3"))
        .willReturn(Optional.of(existing));

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-3");

    assertThat(registration.type()).isEqualTo(CouponIssueRequestRegistrationType.FAILURE_REPLAY);
    assertThat(existing.getReusedCount()).isEqualTo(1);
  }

  @Test
  void 아직_처리중인_요청키가_다시_오면_중복요청으로_해석한다() {
    CouponIssueRequest existing = CouponIssueRequest.createInProgress("idem-4", 1L, 2L);

    given(couponIssueRequestRegistrationWriter.createInProgress("idem-4", 1L, 2L))
        .willThrow(new DataIntegrityViolationException("duplicate"));
    given(couponIssueRequestRepository.findByCouponEventIdAndUserIdAndIdempotencyKey(1L, 2L, "idem-4"))
        .willReturn(Optional.of(existing));

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-4");

    assertThat(registration.type()).isEqualTo(CouponIssueRequestRegistrationType.IN_PROGRESS_DUPLICATE);
    assertThat(existing.getReusedCount()).isEqualTo(1);
    verify(couponIssueRequestRepository).findByCouponEventIdAndUserIdAndIdempotencyKey(eq(1L), eq(2L), eq("idem-4"));
  }
}
