package com.ilway.coupon.coupon.event.api;

import com.ilway.coupon.common.api.ApiResponse;
import com.ilway.coupon.coupon.event.CouponEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/coupon-events")
public class AdminCouponEventController {

  private final CouponEventService couponEventService;

  @GetMapping("/{couponEventId}/statistics")
  public ApiResponse<AdminCouponEventStatisticsResponse> getStatistics(@PathVariable Long couponEventId) {
    return ApiResponse.success(couponEventService.getStatistics(couponEventId));
  }
}
