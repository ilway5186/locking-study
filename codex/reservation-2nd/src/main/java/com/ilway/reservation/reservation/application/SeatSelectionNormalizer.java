package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SeatSelectionNormalizer {

  public NormalizedSeatSelection normalize(List<Long> seatIds) {
    if (seatIds == null || seatIds.isEmpty()) {
      throw new ReservationException(ReservationFailureReason.INVALID_SEAT_SELECTION);
    }
    if (seatIds.stream().anyMatch(Objects::isNull)) {
      throw new ReservationException(ReservationFailureReason.INVALID_SEAT_SELECTION);
    }

    List<Long> sortedSeatIds = seatIds.stream()
        .sorted()
        .toList();

    if (sortedSeatIds.stream().distinct().count() != sortedSeatIds.size()) {
      throw new ReservationException(ReservationFailureReason.INVALID_SEAT_SELECTION);
    }

    return new NormalizedSeatSelection(
        sortedSeatIds,
        sortedSeatIds.stream().map(String::valueOf).collect(Collectors.joining(","))
    );
  }
}
