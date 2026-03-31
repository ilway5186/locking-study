package com.ilway.reservation.seat.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.application.ReservationCurrentState;
import com.ilway.reservation.reservation.application.ReservationStateResolver;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.SeatAvailabilityStatus;
import com.ilway.reservation.reservation.domain.SeatReservation;
import com.ilway.reservation.reservation.domain.SeatReservationRepository;
import com.ilway.reservation.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservation.seat.api.dto.SeatResponse;
import com.ilway.reservation.seat.domain.Seat;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.ShowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {

  private final SeatRepository seatRepository;
  private final ShowRepository showRepository;
  private final SeatReservationRepository reservationRepository;
  private final ReservationStateResolver stateResolver;
  private final Clock clock;

  public SeatService(
      SeatRepository seatRepository,
      ShowRepository showRepository,
      SeatReservationRepository reservationRepository,
      ReservationStateResolver stateResolver,
      Clock clock
  ) {
    this.seatRepository = seatRepository;
    this.showRepository = showRepository;
    this.reservationRepository = reservationRepository;
    this.stateResolver = stateResolver;
    this.clock = clock;
  }

  @Transactional
  public List<SeatResponse> createSeats(CreateSeatsRequest request) {
    if (!showRepository.existsById(request.showId())) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }

    List<String> normalizedSeatNumbers = request.seatNumbers().stream()
        .map(value -> value == null ? null : value.trim().toUpperCase(Locale.ROOT))
        .toList();

    if (normalizedSeatNumbers.stream().anyMatch(value -> value == null || value.isBlank())
        || normalizedSeatNumbers.stream().distinct().count() != normalizedSeatNumbers.size()) {
      throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
    }

    List<SeatResponse> responses = new ArrayList<>();
    for (String seatNumber : normalizedSeatNumbers) {
      if (seatRepository.existsByShowIdAndSeatNumber(request.showId(), seatNumber)) {
        throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
      }
      Seat seat = seatRepository.save(new Seat(request.showId(), seatNumber));
      responses.add(new SeatResponse(seat.getId(), seat.getSeatNumber(), SeatAvailabilityStatus.AVAILABLE, null));
    }
    return responses;
  }

  @Transactional(readOnly = true)
  public List<SeatResponse> getSeats(Long showId) {
    if (!showRepository.existsById(showId)) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }
    List<Seat> seats = seatRepository.findByShowIdOrderBySeatNumberAsc(showId);
    Map<Long, SeatReservation> latestReservations = latestReservationMap(showId);
    Instant now = Instant.now(clock);
    return seats.stream()
        .map(seat -> {
          ReservationCurrentState currentState = stateResolver.resolve(latestReservations.get(seat.getId()), now);
          return new SeatResponse(
              seat.getId(),
              seat.getSeatNumber(),
              currentState.status(),
              currentState.holdExpiresAt()
          );
        })
        .toList();
  }

  private Map<Long, SeatReservation> latestReservationMap(Long showId) {
    Map<Long, SeatReservation> latestReservations = new LinkedHashMap<>();
    for (SeatReservation reservation : reservationRepository.findAllByShowIdOrderBySeatIdAndCreatedAtDesc(showId)) {
      latestReservations.putIfAbsent(reservation.getSeatId(), reservation);
    }
    return latestReservations;
  }
}
