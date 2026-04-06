package com.ilway.reservationsystem.reservation.application;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.show.domain.Show;
import org.springframework.stereotype.Component;

import java.time.Instant;

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
