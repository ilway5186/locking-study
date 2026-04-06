package com.ilway.reservationsystem.seat.api;

import com.ilway.reservationsystem.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservationsystem.seat.api.dto.SeatResponse;
import com.ilway.reservationsystem.seat.application.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

  private final SeatService seatService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SeatResponse createSeats(@Valid @RequestBody CreateSeatsRequest request) {
    return seatService.createSeats(request);
  }

  @GetMapping("/shows/{showId}")
  public List<SeatResponse> getSeats(@PathVariable Long showId) {
    return seatService.getSeats(showId);
  }

}
