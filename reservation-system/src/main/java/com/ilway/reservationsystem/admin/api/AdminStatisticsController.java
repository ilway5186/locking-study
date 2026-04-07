package com.ilway.reservationsystem.admin.api;

import com.ilway.reservationsystem.admin.application.AdminStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

  private final AdminStatisticsService service;

  @GetMapping("/shows/{showId}")
  public AdminStatisticsResponse getShowStatistics(@PathVariable Long showId) {
    return service.getShowStatistics(showId);
  }

}
