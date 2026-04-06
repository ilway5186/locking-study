package com.ilway.reservationsystem.seat.application;

import com.ilway.reservationsystem.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservationsystem.seat.api.dto.SeatResponse;
import com.ilway.reservationsystem.seat.domain.SeatRepository;
import com.ilway.reservationsystem.show.domain.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;

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

  }

  @Transactional(readOnly = true)
  public List<SeatResponse> getSeats(Long showId) {

  }

  private Map<Long, SeatReservation> latestReservationMap(Long showId) {

  }

}
