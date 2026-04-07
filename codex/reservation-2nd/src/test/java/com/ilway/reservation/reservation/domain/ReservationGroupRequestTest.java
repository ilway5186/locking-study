package com.ilway.reservation.reservation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReservationGroupRequestTest {

  @Test
  void 같은_show_user_정규화된_좌석_조합이면_같은_멱등_요청이다() {
    ReservationGroupRequest request = ReservationGroupRequest.hold("idem-1", 10L, 30L, "11,12,13");

    assertTrue(request.matchesPayload(10L, 30L, "11,12,13"));
    assertFalse(request.matchesPayload(10L, 30L, "11,12,14"));
  }

  @Test
  void 성공_실패_재사용_횟수를_요청_이력에_기록할_수_있다() {
    ReservationGroupRequest request = ReservationGroupRequest.hold("idem-1", 10L, 30L, "11,12,13");

    request.markSucceeded(100L);
    request.increaseReusedCount();
    assertEquals(ReservationRequestStatus.SUCCEEDED, request.getRequestStatus());
    assertEquals(100L, request.getReservationGroupId());
    assertEquals(1L, request.getReusedCount());

    request.markFailed(ReservationFailureReason.SEAT_ALREADY_HELD);
    assertEquals(ReservationRequestStatus.FAILED, request.getRequestStatus());
    assertEquals(ReservationFailureReason.SEAT_ALREADY_HELD, request.getFailureReason());
  }
}
