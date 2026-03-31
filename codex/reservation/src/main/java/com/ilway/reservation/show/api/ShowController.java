package com.ilway.reservation.show.api;

import com.ilway.reservation.show.api.dto.CreateShowRequest;
import com.ilway.reservation.show.api.dto.ShowResponse;
import com.ilway.reservation.show.application.ShowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows")
public class ShowController {

  private final ShowService showService;

  public ShowController(ShowService showService) {
    this.showService = showService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
    return showService.createShow(request);
  }
}
