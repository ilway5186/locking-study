package com.ilway.couponsystem.coupon.issue.processor;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import com.ilway.couponsystem.coupon.event.CouponEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

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
