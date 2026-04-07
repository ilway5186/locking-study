package com.ilway.reservation.reservation.application;

import java.util.List;

public record NormalizedSeatSelection(
    List<Long> seatIds,
    String selectionKey
) {
}
