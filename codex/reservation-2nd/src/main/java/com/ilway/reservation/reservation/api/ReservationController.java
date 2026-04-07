package com.ilway.reservation.reservation.api;

import com.ilway.reservation.reservation.api.dto.CancelReservationRequest;
import com.ilway.reservation.reservation.api.dto.ConfirmReservationRequest;
import com.ilway.reservation.reservation.api.dto.HoldSeatRequest;
import com.ilway.reservation.reservation.api.dto.HoldSeatResponse;
import com.ilway.reservation.reservation.api.dto.MyReservationResponse;
import com.ilway.reservation.reservation.api.dto.ReservationResponse;
import com.ilway.reservation.reservation.application.HoldSeatCommand;
import com.ilway.reservation.reservation.application.SeatReservationFacade;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

  private final SeatReservationFacade reservationFacade;

  public ReservationController(SeatReservationFacade reservationFacade) {
    this.reservationFacade = reservationFacade;
  }

  @PostMapping("/hold")
  @ResponseStatus(HttpStatus.CREATED)
  public HoldSeatResponse hold(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody HoldSeatRequest request
  ) {
    return reservationFacade.hold(new HoldSeatCommand(idempotencyKey, request.showId(), request.seatId(), request.userId()));
  }

  @PostMapping("/{reservationId}/confirm")
  public ReservationResponse confirm(
      @PathVariable Long reservationId,
      @Valid @RequestBody ConfirmReservationRequest request
  ) {
    return reservationFacade.confirm(reservationId, request.userId());
  }

  @PostMapping("/{reservationId}/cancel")
  public ReservationResponse cancel(
      @PathVariable Long reservationId,
      @Valid @RequestBody CancelReservationRequest request
  ) {
    return reservationFacade.cancel(reservationId, request.userId());
  }

  @GetMapping
  public List<MyReservationResponse> getMyReservations(@RequestParam Long userId) {
    return reservationFacade.getMyReservations(userId);
  }
}
