package com.ilway.reservation.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeatSelectionNormalizerTest {

  private final SeatSelectionNormalizer normalizer = new SeatSelectionNormalizer();

  @Test
  void 좌석_선택은_오름차순으로_정규화되어_같은_조합을_같은_논리_요청으로_본다() {
    NormalizedSeatSelection selection = normalizer.normalize(List.of(13L, 11L, 12L));

    assertEquals(List.of(11L, 12L, 13L), selection.seatIds());
    assertEquals("11,12,13", selection.selectionKey());
  }

  @Test
  void 중복_좌석이_들어오면_invalid_seat_selection이다() {
    ReservationException exception = assertThrows(ReservationException.class, () ->
        normalizer.normalize(List.of(11L, 12L, 11L)));

    assertEquals(ReservationFailureReason.INVALID_SEAT_SELECTION, exception.getReason());
  }
}
