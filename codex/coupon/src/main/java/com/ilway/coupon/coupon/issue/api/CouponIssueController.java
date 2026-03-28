package com.ilway.coupon.coupon.issue.api;

import com.ilway.coupon.common.api.ApiResponse;
import com.ilway.coupon.coupon.issue.CouponIssueService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class CouponIssueController {

  private final CouponIssueService couponIssueService;

  @PostMapping("/coupon-events/{couponEventId}/issues")
  public ApiResponse<CouponIssueResponse> issue(
      @PathVariable Long couponEventId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody IssueCouponRequest request
  ) {
    return ApiResponse.success(couponIssueService.issue(couponEventId, request.userId(), idempotencyKey));
  }

  @GetMapping("/users/{userId}/coupon-issues")
  public ApiResponse<List<UserCouponIssueResponse>> getUserIssues(@PathVariable Long userId) {
    return ApiResponse.success(couponIssueService.getUserIssues(userId));
  }
}
