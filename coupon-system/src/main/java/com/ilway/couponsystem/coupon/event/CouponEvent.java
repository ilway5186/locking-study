package com.ilway.couponsystem.coupon.event;

import com.ilway.couponsystem.common.exception.BusinessException;
import com.ilway.couponsystem.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Accessors(fluent = true)
@Entity
@Table(name = "coupon_event")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "total_quantity", nullable = false)
  private Integer totalQuantity;

  @Column(name = "issued_quantity", nullable = false)
  private Integer issuedQuantity;

  @Column(name = "start_at", nullable = false)
  private LocalDateTime startAt;

  @Column(name = "end_at", nullable = false)
  private LocalDateTime endAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false, updatable = true)
  private LocalDateTime updatedAt;

  private CouponEvent(String name, int totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
    validateCreateArguments(name, totalQuantity, startAt, endAt);
    this.name = name;
    this.totalQuantity = totalQuantity;
    this.issuedQuantity = 0;
    this.startAt = startAt;
    this.endAt = endAt;
  }

  public static CouponEvent create(String name, int totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
    return new CouponEvent(name, totalQuantity, startAt, endAt);
  }

  public void issueOne(LocalDateTime now) {
    validateIssuableAt(now);
    if (isSoldOut()) {
      throw new BusinessException(ErrorCode.SOLD_OUT);
    }
    issuedQuantity++;
  }

  public int remainingQuantity() {
    return totalQuantity - issuedQuantity;
  }

  public boolean isSoldOut() {
    return issuedQuantity >= totalQuantity;
  }

  public CouponEventStatus statusAt(LocalDateTime now) {
    return CouponEventStatus.from(this, now);
  }

  public boolean isIssuableAt(LocalDateTime now) {
    return !now.isBefore(startAt) && !now.isAfter(endAt);
  }

  public void validateIssuableAt(LocalDateTime now) {
    if (now.isBefore(startAt) || now.isAfter(endAt)) {
      throw new BusinessException(ErrorCode.ISSUE_PERIOD_CLOSED);
    }
  }

  private static void validateCreateArguments(String name, int totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("이벤트 이름은 비어 있을 수 없습니다.");
    }
    if (totalQuantity <= 0) {
      throw new IllegalArgumentException("총 발급 수량은 1 이상이어야 합니다.");
    }
    if (startAt == null || endAt == null) {
      throw new IllegalArgumentException("발급 시작/종료 시각은 필수입니다.");
    }
    if (startAt.isAfter(endAt)) {
      throw new IllegalArgumentException("발급 시작 시각이 종료 시각보다 뒤일 수 없습니다.");
    }
  }

}
