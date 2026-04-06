package com.ilway.reservationsystem.show.application;

import com.ilway.reservationsystem.common.exception.ReservationException;
import com.ilway.reservationsystem.reservation.domain.ReservationFailureReason;
import com.ilway.reservationsystem.show.api.dto.CreateShowRequest;
import com.ilway.reservationsystem.show.api.dto.ShowResponse;
import com.ilway.reservationsystem.show.domain.Show;
import com.ilway.reservationsystem.show.domain.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShowService {

  private final ShowRepository repo;

  @Transactional
  public ShowResponse createShow(CreateShowRequest request) {
    validate(request);
    Show savedShow = repo.save(new Show(
      request.name(),
      request.startAt(),
      request.endAt(),
      request.bookingOpenAt(),
      request.bookingCloseAt()
    ));

    return new ShowResponse(
      savedShow.getId(),
      savedShow.getName(),
      savedShow.getStartAt(),
      savedShow.getEndAt(),
      savedShow.getBookingOpenAt(),
      savedShow.getBookingCloseAt()
    );
  }

  private void validate(CreateShowRequest request) {
    if (!request.startAt().isAfter(request.bookingCloseAt())
      || !request.endAt().isBefore(request.startAt())
      || !request.bookingOpenAt().isAfter(request.bookingCloseAt())) {

      throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
    }
  }


}
