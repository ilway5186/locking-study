package com.ilway.couponsystem.coupon.issue.api;

import com.ilway.couponsystem.common.api.ApiResponse;
import com.ilway.couponsystem.coupon.issue.CouponIssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

  private final CouponIssueService couponIssueService;

  @PostMapping("/coupon-events/{couponEventId}/issues")
  public ApiResponse<CouponIssueResponse> issue(
    @PathVariable Long couponEventId,
    @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey,
    @Valid @RequestBody IssueCouponRequest request
  ) {
    return ApiResponse.success(couponIssueService.issue(couponEventId, request.userId(), idempotencyKey));
  }

  @GetMapping("/users/{userId}/coupon-issues")
  public ApiResponse<List<UserCouponIssueResponse>> getUserIssues(@PathVariable Long userId) {
    return ApiResponse.success(couponIssueService.getUserIssues(userId));
  }

}
