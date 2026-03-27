package com.ilway.coupon.common.api;

public record ErrorResponse(
    String code,
    String message
) {
}
