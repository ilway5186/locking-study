package com.ilway.coupon.comparison.conditional;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import com.ilway.coupon.coupon.event.CouponEvent;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

@Component
public class ConditionalIssueFailureResolver {

  public BusinessException resolve(CouponEvent couponEvent, LocalDateTime now) {
    if (!couponEvent.isIssuableAt(now)) {
      return new BusinessException(ErrorCode.ISSUE_PERIOD_CLOSED);
    }
    if (couponEvent.isSoldOut()) {
      return new BusinessException(ErrorCode.SOLD_OUT);
    }
    return new BusinessException(ErrorCode.SOLD_OUT, "조건부 UPDATE가 실패했고, 최신 이벤트 상태는 이미 소진 상태로 해석했습니다.");
  }
}
