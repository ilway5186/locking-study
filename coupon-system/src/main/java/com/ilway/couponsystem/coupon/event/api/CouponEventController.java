package com.ilway.couponsystem.coupon.event.api;

import com.ilway.couponsystem.common.api.ApiResponse;
import com.ilway.couponsystem.coupon.event.CouponEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupon-events")
@RequiredArgsConstructor
public class CouponEventController {

  private final CouponEventService couponEventService;

  @PostMapping
  public ApiResponse<CouponEventResponse> create(@Valid @RequestBody CreateCouponEventRequest request) {
    return ApiResponse.success(couponEventService.create(request));
  }

  @GetMapping("/{couponEventId}")
  public ApiResponse<CouponEventResponse> get(@PathVariable Long couponEventId) {
    return ApiResponse.success(couponEventService.get(couponEventId));
  }

}
