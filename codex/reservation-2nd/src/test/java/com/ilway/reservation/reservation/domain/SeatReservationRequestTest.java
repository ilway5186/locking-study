package com.ilway.reservation.reservation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeatReservationRequestTest {

  @Test
  void 같은_show_seat_user면_같은_멱등_요청이다() {
    SeatReservationRequest request = SeatReservationRequest.hold("idem-1", 10L, 20L, 30L);

    assertTrue(request.matchesPayload(10L, 20L, 30L));
    assertFalse(request.matchesPayload(10L, 21L, 30L));
  }

  @Test
  void 성공과_실패를_요청_이력에_기록할_수_있다() {
    SeatReservationRequest request = SeatReservationRequest.hold("idem-1", 10L, 20L, 30L);

    request.markSucceeded(100L);
    assertEquals(ReservationRequestStatus.SUCCEEDED, request.getRequestStatus());
    assertEquals(100L, request.getReservationId());

    request.markFailed(ReservationFailureReason.SEAT_ALREADY_HELD);
    assertEquals(ReservationRequestStatus.FAILED, request.getRequestStatus());
    assertEquals(ReservationFailureReason.SEAT_ALREADY_HELD, request.getFailureReason());
  }

  @Test
  void 재사용_횟수를_누적한다() {
    SeatReservationRequest request = SeatReservationRequest.hold("idem-1", 10L, 20L, 30L);

    request.increaseReusedCount();
    request.increaseReusedCount();

    assertEquals(2L, request.getReusedCount());
    assertNull(request.getFailureReason());
  }
}
