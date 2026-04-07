package com.ilway.reservationsystem.seat.application;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.application.ReservationStateResolver;
import com.ilway.reservationsystem.reservation.application.vo.ReservationCurrentState;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.reservation.domain.SeatAvailabilityStatus;
import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.repository.SeatReservationRepository;
import com.ilway.reservationsystem.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservationsystem.seat.api.dto.SeatResponse;
import com.ilway.reservationsystem.seat.domain.Seat;
import com.ilway.reservationsystem.seat.domain.SeatRepository;
import com.ilway.reservationsystem.show.domain.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SeatService {

  private final SeatRepository seatRepo;
  private final ShowRepository showRepo;
  private final SeatReservationRepository reservationRepo;
  private final ReservationStateResolver stateResolver;
  private final Clock clock;


  @Transactional
  public List<SeatResponse> createSeats(CreateSeatsRequest request) {
    if (!showRepo.existsById(request.showId())) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }

    List<String> normalizedSeatNumbers = request.seatNumbers().stream()
      .map(value -> value == null ? null : value.trim().toUpperCase())
      .toList();

    if (normalizedSeatNumbers.stream().anyMatch(value -> value == null || value.isBlank())
      || normalizedSeatNumbers.stream().distinct().count() != normalizedSeatNumbers.size()) {
      throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
    }

    List<SeatResponse> responses = new ArrayList<>();
    for (String seatNumber : normalizedSeatNumbers) {
      if (seatRepo.existsByShowIdAndSeatNumber(request.showId(), seatNumber)) {
        throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
      }

      Seat createdSeat = seatRepo.save(new Seat(request.showId(), seatNumber));
      responses.add(new SeatResponse(createdSeat.getId(), createdSeat.getSeatNumber(), SeatAvailabilityStatus.AVAILABLE, null));
    }

    return responses;
  }

  @Transactional(readOnly = true)
  public List<SeatResponse> getSeats(Long showId) {
    if (!showRepo.existsById(showId)) {
      throw new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND);
    }

    List<Seat> seats = seatRepo.findByShowIdOrderBySeatNumberAsc(showId);
    Map<Long, SeatReservation> latestReservation = latestReservationMap(showId);

    Instant now = Instant.now(clock);
    return seats.stream().map(seat -> {
      ReservationCurrentState currentState = stateResolver.resolve(latestReservation.get(seat.getId()), now);
      return new SeatResponse(
        seat.getId(),
        seat.getSeatNumber(),
        currentState.status(),
        currentState.holdExpiresAt()
      );
    }).toList();
  }

  private Map<Long, SeatReservation> latestReservationMap(Long showId) {
    Map<Long, SeatReservation> latestReservations = new LinkedHashMap<>();
    for (SeatReservation reservation : reservationRepo.findAllByShowIdOrderBySeatIdAndCreatedAtDesc(showId)) {
      latestReservations.putIfAbsent(reservation.getSeatId(), reservation);
    }
    return latestReservations;
  }

}
