package com.ilway.reservationsystem.show.api;

import com.ilway.reservationsystem.show.api.dto.CreateShowRequest;
import com.ilway.reservationsystem.show.api.dto.ShowResponse;
import com.ilway.reservationsystem.show.application.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

  private final ShowService showService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
    return showService.createShow(request);
  }

}
