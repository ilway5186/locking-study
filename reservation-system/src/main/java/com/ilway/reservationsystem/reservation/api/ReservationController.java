package com.ilway.reservationsystem.reservation.api;

import com.ilway.reservationsystem.reservation.api.dto.*;
import com.ilway.reservationsystem.reservation.application.SeatReservationFacade;
import com.ilway.reservationsystem.reservation.application.vo.HoldSeatCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

  private final SeatReservationFacade reservationFacade;

  @GetMapping("/hold")
  @ResponseStatus(HttpStatus.CREATED)
  public HoldSeatResponse hold(
    @RequestHeader("Idempotency-Key") String idempotencyKey,
    @Valid @RequestBody HoldSeatRequest request
  ) {
    return reservationFacade.hold(new HoldSeatCommand(
      idempotencyKey,
      request.showId(),
      request.seatId(),
      request.userId()
    ));
  }

  @PostMapping("/{reservationId}/confirm")
  public ReservationResponse confirm(@PathVariable Long reservationId, @Valid @RequestBody ConfirmReservationRequest request) {
    return reservationFacade.confirm(reservationId, request.userId());
  }

  @PostMapping("/{reservationId}/cancel")
  public ReservationResponse cancel(@PathVariable Long reservationId, @Valid @RequestBody CancelReservationRequest request) {
    return reservationFacade.cancel(reservationId, request.userId());
  }

  @GetMapping
  public List<MyReservationResponse> getMyReservation(@RequestParam Long userId) {
    return reservationFacade.getMyReservations(userId);
  }


}
