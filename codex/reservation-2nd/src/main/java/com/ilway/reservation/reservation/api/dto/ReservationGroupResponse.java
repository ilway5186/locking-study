package com.ilway.reservation.reservation.api.dto;

import com.ilway.reservation.reservation.domain.ReservationGroupStatus;
import java.time.Instant;
import java.util.List;

public record ReservationGroupResponse(
    Long reservationGroupId,
    Long showId,
    Long userId,
    ReservationGroupStatus status,
    Instant holdExpiresAt,
    Instant paymentPendingAt,
    Instant reservedAt,
    Instant cancelledAt,
    Instant expiredAt,
    List<ReservationGroupSeatResponse> seats
) {
}
