package com.ilway.reservation.show.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.show.api.dto.CreateShowRequest;
import com.ilway.reservation.show.api.dto.ShowResponse;
import com.ilway.reservation.show.domain.Show;
import com.ilway.reservation.show.domain.ShowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShowService {

  private final ShowRepository showRepository;

  public ShowService(ShowRepository showRepository) {
    this.showRepository = showRepository;
  }

  @Transactional
  public ShowResponse createShow(CreateShowRequest request) {
    validate(request);
    Show show = showRepository.save(new Show(
        request.name(),
        request.startAt(),
        request.endAt(),
        request.bookingOpenAt(),
        request.bookingCloseAt()
    ));
    return new ShowResponse(
        show.getId(),
        show.getName(),
        show.getStartAt(),
        show.getEndAt(),
        show.getBookingOpenAt(),
        show.getBookingCloseAt()
    );
  }

  private void validate(CreateShowRequest request) {
    if (!request.startAt().isAfter(request.bookingCloseAt())
        || !request.endAt().isAfter(request.startAt())
        || !request.bookingCloseAt().isAfter(request.bookingOpenAt())) {
      throw new ReservationException(ReservationFailureReason.INVALID_REQUEST);
    }
  }
}
