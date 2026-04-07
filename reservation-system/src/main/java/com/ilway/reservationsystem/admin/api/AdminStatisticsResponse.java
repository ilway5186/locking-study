package com.ilway.reservationsystem.admin.api;

import java.util.Map;

public record AdminStatisticsResponse(
  long totalSeatCount,
  long holdCount,
  long reservedCount,
  long expiredCount,
  long cancelledCount,
  long requestCount,
  long failedRequestCount,
  long idempotentReuseCount,
  Map<String, Long> failureReasonCounts
) {
}
