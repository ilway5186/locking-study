package com.ilway.reservation.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationGroup;
import com.ilway.reservation.reservation.domain.ReservationGroupRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupSeat;
import com.ilway.reservation.reservation.domain.ReservationGroupSeatRepository;
import com.ilway.reservation.seat.domain.Seat;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.ShowRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReservationGroupTxServiceTest {

  private final ShowRepository showRepository = mock(ShowRepository.class);
  private final SeatRepository seatRepository = mock(SeatRepository.class);
  private final ReservationGroupRepository reservationGroupRepository = mock(ReservationGroupRepository.class);
  private final ReservationGroupSeatRepository reservationGroupSeatRepository = mock(ReservationGroupSeatRepository.class);
  private final BookingWindowPolicy bookingWindowPolicy = mock(BookingWindowPolicy.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-03-31T00:00:01Z"), ZoneOffset.UTC);

  private final ReservationGroupTxService service = new ReservationGroupTxService(
      showRepository,
      seatRepository,
      reservationGroupRepository,
      reservationGroupSeatRepository,
      bookingWindowPolicy,
      clock,
      Duration.ofMinutes(5)
  );

  @Test
  void payment_pending인_좌석이_끼어있으면_명확한_실패_사유를_반환한다() {
    ReservationGroup existingGroup = ReservationGroup.hold(
        10L,
        7L,
        Instant.parse("2026-03-31T00:10:00Z"),
        List.of(new Seat(10L, "A3"))
    );
    existingGroup.moveToPaymentPending(7L, Instant.parse("2026-03-31T00:00:00Z"));
    ReservationGroupSeat existingSeat = existingGroup.getSeats().getFirst();

    when(showRepository.findById(10L)).thenReturn(Optional.of(mock(com.ilway.reservation.show.domain.Show.class)));
    when(seatRepository.findAllByShowIdAndIdInOrderByIdAscForUpdate(10L, List.of(3L)))
        .thenReturn(List.of(new Seat(10L, "A3")));
    when(reservationGroupSeatRepository.findAllWithGroupBySeatIdInOrderBySeatIdAscCreatedAtDescIdDesc(List.of(3L)))
        .thenReturn(List.of(existingSeat));

    ReservationException exception = assertThrows(ReservationException.class, () ->
        service.processHold(10L, 20L, List.of(3L)));

    assertEquals(ReservationFailureReason.SEAT_PAYMENT_PENDING, exception.getReason());
  }
}
