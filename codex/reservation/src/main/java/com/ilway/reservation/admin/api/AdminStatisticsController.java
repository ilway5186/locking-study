package com.ilway.reservation.admin.api;

import com.ilway.reservation.admin.api.dto.AdminStatisticsResponse;
import com.ilway.reservation.admin.application.AdminStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {

  private final AdminStatisticsService adminStatisticsService;

  public AdminStatisticsController(AdminStatisticsService adminStatisticsService) {
    this.adminStatisticsService = adminStatisticsService;
  }

  @GetMapping("/shows/{showId}")
  public AdminStatisticsResponse getShowStatistics(@PathVariable Long showId) {
    return adminStatisticsService.getShowStatistics(showId);
  }
}
