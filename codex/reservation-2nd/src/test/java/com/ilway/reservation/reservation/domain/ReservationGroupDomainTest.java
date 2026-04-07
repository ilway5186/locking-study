package com.ilway.reservation.reservation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.seat.domain.Seat;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReservationGroupDomainTest {

  @Test
  void hold는_만료_시각_이후_expired로_판정된다() {
    ReservationGroup group = ReservationGroup.hold(
        1L,
        7L,
        Instant.parse("2026-03-31T00:00:02Z"),
        List.of(new Seat(1L, "A1"), new Seat(1L, "A2"))
    );

    assertTrue(group.isExpiredAt(Instant.parse("2026-03-31T00:00:02Z")));
  }

  @Test
  void hold는_payment_pending을_거쳐_reserved로_확정된다() {
    ReservationGroup group = ReservationGroup.hold(
        1L,
        7L,
        Instant.parse("2026-03-31T00:00:10Z"),
        List.of(new Seat(1L, "A1"), new Seat(1L, "A2"))
    );

    group.moveToPaymentPending(7L, Instant.parse("2026-03-31T00:00:01Z"));
    group.reserve(7L, Instant.parse("2026-03-31T00:00:01Z"));

    assertEquals(ReservationGroupStatus.RESERVED, group.getStatus());
    assertEquals(ReservationGroupSeatStatus.RESERVED, group.getSeats().getFirst().getStatus());
  }

  @Test
  void 만료된_hold를_확정하면_hold_expired를_반환한다() {
    ReservationGroup group = ReservationGroup.hold(
        1L,
        7L,
        Instant.parse("2026-03-31T00:00:02Z"),
        List.of(new Seat(1L, "A1"), new Seat(1L, "A2"))
    );

    ReservationException exception = assertThrows(ReservationException.class, () ->
        group.moveToPaymentPending(7L, Instant.parse("2026-03-31T00:00:02Z")));

    assertEquals(ReservationFailureReason.HOLD_EXPIRED, exception.getReason());
    assertEquals(ReservationGroupStatus.EXPIRED, group.getStatus());
  }
}
