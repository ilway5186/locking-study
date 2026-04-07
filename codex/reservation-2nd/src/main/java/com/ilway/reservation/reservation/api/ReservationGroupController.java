package com.ilway.reservation.reservation.api;

import com.ilway.reservation.reservation.api.dto.CancelReservationGroupRequest;
import com.ilway.reservation.reservation.api.dto.ConfirmReservationGroupRequest;
import com.ilway.reservation.reservation.api.dto.HoldReservationGroupRequest;
import com.ilway.reservation.reservation.api.dto.HoldReservationGroupResponse;
import com.ilway.reservation.reservation.api.dto.ReservationGroupResponse;
import com.ilway.reservation.reservation.application.HoldReservationGroupCommand;
import com.ilway.reservation.reservation.application.ReservationGroupFacade;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReservationGroupController {

  private final ReservationGroupFacade reservationGroupFacade;

  public ReservationGroupController(ReservationGroupFacade reservationGroupFacade) {
    this.reservationGroupFacade = reservationGroupFacade;
  }

  @PostMapping("/shows/{showId}/reservation-groups/hold")
  @ResponseStatus(HttpStatus.CREATED)
  public HoldReservationGroupResponse hold(
      @PathVariable Long showId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody HoldReservationGroupRequest request
  ) {
    return reservationGroupFacade.hold(new HoldReservationGroupCommand(
        idempotencyKey,
        showId,
        request.userId(),
        request.seatIds()
    ));
  }

  @GetMapping("/reservation-groups/{groupId}")
  public ReservationGroupResponse getGroup(@PathVariable Long groupId) {
    return reservationGroupFacade.getGroup(groupId);
  }

  @GetMapping("/users/{userId}/reservation-groups")
  public List<ReservationGroupResponse> getUserGroups(@PathVariable Long userId) {
    return reservationGroupFacade.getUserGroups(userId);
  }

  @PostMapping("/reservation-groups/{groupId}/confirm")
  public ReservationGroupResponse confirm(
      @PathVariable Long groupId,
      @Valid @RequestBody ConfirmReservationGroupRequest request
  ) {
    return reservationGroupFacade.confirm(groupId, request.userId());
  }

  @PostMapping("/reservation-groups/{groupId}/cancel")
  public ReservationGroupResponse cancel(
      @PathVariable Long groupId,
      @Valid @RequestBody CancelReservationGroupRequest request
  ) {
    return reservationGroupFacade.cancel(groupId, request.userId());
  }
}
