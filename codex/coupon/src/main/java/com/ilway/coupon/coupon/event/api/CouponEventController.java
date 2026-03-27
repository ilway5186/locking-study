package com.ilway.coupon.coupon.event.api;

import com.ilway.coupon.common.api.ApiResponse;
import com.ilway.coupon.coupon.event.CouponEventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/coupon-events")
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
