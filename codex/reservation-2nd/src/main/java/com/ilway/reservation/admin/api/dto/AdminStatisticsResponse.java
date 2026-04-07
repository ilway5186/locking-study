package com.ilway.reservation.admin.api.dto;

import java.util.Map;

public record AdminStatisticsResponse(
    long totalSeatCount,
    long totalGroupRequestCount,
    long successfulHoldGroupCount,
    long failedGroupRequestCount,
    long idempotentReuseCount,
    long seatConflictFailureCount,
    Map<String, Long> groupStatusCounts,
    Map<String, Long> failureReasonCounts
) {
}
