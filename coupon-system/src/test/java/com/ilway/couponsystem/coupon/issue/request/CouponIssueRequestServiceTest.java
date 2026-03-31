package com.ilway.couponsystem.coupon.issue.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistration;
import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistrationType;
import com.ilway.couponsystem.coupon.issue.request.registration.CouponIssueRequestRegistrationWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class CouponIssueRequestServiceTest {

  @Mock CouponIssueRequestRepository couponIssueRequestRepo;
  @Mock CouponIssueRequestRegistrationWriter writer;
  @InjectMocks CouponIssueRequestService couponIssueRequestService;

  @Test
  void 새로운_멱등성키는_새로운_요청으로_등록한다() {
    CouponIssueRequest saved = CouponIssueRequest.createInProgress("idem-1", 1L, 1L);
    BDDMockito.given(writer.createInProgress("idem-1", 1L, 1L)).willReturn(saved);

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 1L, "idem-1");
    assertEquals(CouponIssueRequestRegistrationType.NEW, registration.type());
    BDDMockito.verify(writer).createInProgress("idem-1", 1L, 1L);
  }

  @Test
  void 성공했던_요청키가_다시오면_성공결과를_재사용한다() {
    CouponIssueRequest existing = CouponIssueRequest.createInProgress("idem-2", 1L, 2L);
    existing.markSuccess(10L);

    given(writer.createInProgress("idem-2", 1L, 2L))
      .willThrow(new DataIntegrityViolationException("duplicate"));

    given((couponIssueRequestRepo.findByCouponEventIdAndUserIdAndIdempotencyKey(1L, 2L, "idem-2")))
      .willReturn(Optional.of(existing));

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-2");

    assertEquals(CouponIssueRequestRegistrationType.SUCCESS_REPLAY, registration.type());
    assertEquals(1, existing.reusedCount());
  }

  @Test
  void 실패했던_요청키가_다시오면_실패결과를_재사용한다() {
    CouponIssueRequest existing = CouponIssueRequest.createInProgress("idem-3", 1L, 2L);
    existing.markFailed(CouponIssueFailureReason.CONFLICT_RETRY_EXCEEDED);

    given(writer.createInProgress("idem-3", 1L, 2L))
      .willThrow(new DataIntegrityViolationException("duplicate"));

    given((couponIssueRequestRepo.findByCouponEventIdAndUserIdAndIdempotencyKey(1L, 2L, "idem-3")))
      .willReturn(Optional.of(existing));

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-3");
    assertEquals(CouponIssueRequestRegistrationType.FAILURE_REPLAY, registration.type());
    assertEquals(1, existing.reusedCount());
  }

  @Test
  void 아직_처리중인_요청키가_다시오면_중복요청으로_처리한다() {
    CouponIssueRequest existing = CouponIssueRequest.createInProgress("idem-4", 1L, 2L);
    given(writer.createInProgress("idem-4", 1L, 2L))
      .willThrow(new DataIntegrityViolationException("duplicate"));

    given((couponIssueRequestRepo.findByCouponEventIdAndUserIdAndIdempotencyKey(1L, 2L, "idem-4")))
      .willReturn(Optional.of(existing));

    CouponIssueRequestRegistration registration = couponIssueRequestService.register(1L, 2L, "idem-4");

    assertEquals(CouponIssueRequestRegistrationType.IN_PROGRESS_DUPLICATE, registration.type());
    assertEquals(1, existing.reusedCount());
    verify(couponIssueRequestRepo)
      .findByCouponEventIdAndUserIdAndIdempotencyKey(eq(1L), eq(2L), eq("idem-4"));

  }

}
