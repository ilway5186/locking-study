package com.ilway.reservationsystem.reservation.application;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.show.domain.Show;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingWindowPolicyTest {

  private final BookingWindowPolicy bookingWindowPolicy = new BookingWindowPolicy();

  @Test
  void 오픈_전이면_예매할_수_없다() {
    Show show = new Show(
        "concert",
        Instant.parse("2026-04-01T10:00:00Z"),
        Instant.parse("2026-04-01T12:00:00Z"),
        Instant.parse("2026-03-31T10:00:00Z"),
        Instant.parse("2026-04-01T09:00:00Z")
    );

    assertThrows(ReservationException.class, () ->
        bookingWindowPolicy.validateHoldable(show, Instant.parse("2026-03-31T09:59:59Z")));
  }

  @Test
  void 마감_이후면_예매할_수_없다() {
    Show show = new Show(
        "concert",
        Instant.parse("2026-04-01T10:00:00Z"),
        Instant.parse("2026-04-01T12:00:00Z"),
        Instant.parse("2026-03-31T10:00:00Z"),
        Instant.parse("2026-04-01T09:00:00Z")
    );

    assertThrows(ReservationException.class, () ->
        bookingWindowPolicy.validateHoldable(show, Instant.parse("2026-04-01T09:00:01Z")));
  }

  @Test
  void 예매_가능_시간이면_hold를_허용한다() {
    Show show = new Show(
        "concert",
        Instant.parse("2026-04-01T10:00:00Z"),
        Instant.parse("2026-04-01T12:00:00Z"),
        Instant.parse("2026-03-31T10:00:00Z"),
        Instant.parse("2026-04-01T09:00:00Z")
    );

    assertDoesNotThrow(() ->
        bookingWindowPolicy.validateHoldable(show, Instant.parse("2026-03-31T10:00:00Z")));
  }
}
