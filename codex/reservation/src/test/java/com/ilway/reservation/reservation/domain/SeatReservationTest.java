package com.ilway.reservation.reservation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ilway.reservation.common.exception.ReservationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SeatReservationTest {

  @Test
  void hold는_만료_시각_이후_expired로_판정된다() {
    SeatReservation reservation = SeatReservation.hold(1L, 2L, 3L, Instant.parse("2026-03-31T00:00:02Z"));

    assertTrue(reservation.isExpiredAt(Instant.parse("2026-03-31T00:00:02Z")));
  }

  @Test
  void hold는_만료_전이면_reserved로_확정할_수_있다() {
    SeatReservation reservation = SeatReservation.hold(1L, 2L, 3L, Instant.parse("2026-03-31T00:00:02Z"));

    reservation.confirm(3L, Instant.parse("2026-03-31T00:00:01Z"));

    assertEquals(SeatReservationStatus.RESERVED, reservation.getStatus());
  }

  @Test
  void 만료된_hold를_확정하면_hold_expired를_반환한다() {
    SeatReservation reservation = SeatReservation.hold(1L, 2L, 3L, Instant.parse("2026-03-31T00:00:02Z"));

    ReservationException exception = assertThrows(ReservationException.class, () ->
        reservation.confirm(3L, Instant.parse("2026-03-31T00:00:02Z")));

    assertEquals(ReservationFailureReason.HOLD_EXPIRED, exception.getReason());
    assertEquals(SeatReservationStatus.EXPIRED, reservation.getStatus());
  }

  @Test
  void 다른_사용자는_취소할_수_없다() {
    SeatReservation reservation = SeatReservation.hold(1L, 2L, 3L, Instant.parse("2026-03-31T00:00:02Z"));

    ReservationException exception = assertThrows(ReservationException.class, () ->
        reservation.cancel(99L, Instant.parse("2026-03-31T00:00:01Z")));

    assertEquals(ReservationFailureReason.FORBIDDEN_RESERVATION_ACCESS, exception.getReason());
  }
}
