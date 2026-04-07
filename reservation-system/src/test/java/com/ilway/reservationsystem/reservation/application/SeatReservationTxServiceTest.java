package com.ilway.reservationsystem.reservation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.application.service.SeatReservationTxService;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRepository;
import com.ilway.reservationsystem.seat.domain.Seat;
import com.ilway.reservationsystem.seat.domain.SeatRepository;
import com.ilway.reservationsystem.show.domain.ShowRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class SeatReservationTxServiceTest {

  private final ShowRepository showRepository = mock(ShowRepository.class);
  private final SeatRepository seatRepository = mock(SeatRepository.class);
  private final SeatReservationRepository reservationRepository = mock(SeatReservationRepository.class);
  private final BookingWindowPolicy bookingWindowPolicy = mock(BookingWindowPolicy.class);
  private final EntityManager entityManager = mock(EntityManager.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-03-31T00:00:01Z"), ZoneOffset.UTC);

  private final SeatReservationTxService service = new SeatReservationTxService(
    showRepository,
    seatRepository,
    reservationRepository,
    bookingWindowPolicy,
    entityManager,
    clock,
    Duration.ofMinutes(5)
  );

  @Test
  void confirm은_seat_lock_이후_refresh된_최신_상태를_기준으로_검증한다() {
    SeatReservation reservation = SeatReservation.hold(10L, 20L, 7L, Instant.parse("2026-03-31T00:00:10Z"));
    Seat seat = new Seat(10L, "A1");
    Instant cancelledAt = Instant.parse("2026-03-31T00:00:00Z");

    when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
    when(seatRepository.findByIdAndShowIdForUpdate(20L, 10L)).thenReturn(Optional.of(seat));
    doAnswer(invocation -> {
      ReflectionTestUtils.setField(reservation, "status", SeatReservationStatus.CANCELLED);
      ReflectionTestUtils.setField(reservation, "cancelledAt", cancelledAt);
      return null;
    }).when(entityManager).refresh(reservation);

    ReservationException exception = assertThrows(ReservationException.class, () -> service.confirm(1L, 7L));

    assertEquals(ReservationFailureReason.INVALID_RESERVATION_STATE, exception.getReason());
    verify(entityManager).refresh(reservation);
    verify(reservationRepository, times(1)).findById(1L);
  }

}
