package com.ilway.coupon.coupon.event.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CreateCouponEventRequest(
    @NotBlank(message = "이벤트 이름은 필수입니다.")
    String name,

    @Positive(message = "총 발급 수량은 1 이상이어야 합니다.")
    int totalQuantity,

    @NotNull(message = "발급 시작 시각은 필수입니다.")
    LocalDateTime startAt,

    @NotNull(message = "발급 종료 시각은 필수입니다.")
    LocalDateTime endAt
) {
}
