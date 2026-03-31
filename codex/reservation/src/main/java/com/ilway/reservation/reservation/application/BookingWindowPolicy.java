package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.show.domain.Show;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class BookingWindowPolicy {

  public void validateHoldable(Show show, Instant now) {
    if (now.isBefore(show.getBookingOpenAt())) {
      throw new ReservationException(ReservationFailureReason.BOOKING_NOT_OPEN);
    }
    if (now.isAfter(show.getBookingCloseAt())) {
      throw new ReservationException(ReservationFailureReason.BOOKING_CLOSED);
    }
  }
}
