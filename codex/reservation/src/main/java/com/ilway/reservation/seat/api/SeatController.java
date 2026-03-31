package com.ilway.reservation.seat.api;

import com.ilway.reservation.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservation.seat.api.dto.SeatResponse;
import com.ilway.reservation.seat.application.SeatService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

  private final SeatService seatService;

  public SeatController(SeatService seatService) {
    this.seatService = seatService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public List<SeatResponse> createSeats(@Valid @RequestBody CreateSeatsRequest request) {
    return seatService.createSeats(request);
  }

  @GetMapping("/shows/{showId}")
  public List<SeatResponse> getSeats(@PathVariable Long showId) {
    return seatService.getSeats(showId);
  }
}
