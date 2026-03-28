package com.ilway.coupon.coupon.event;

import com.ilway.coupon.common.exception.BusinessException;
import com.ilway.coupon.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "coupon_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private int totalQuantity;

  @Column(nullable = false)
  private int issuedQuantity;

  @Column(nullable = false)
  private LocalDateTime startAt;

  @Column(nullable = false)
  private LocalDateTime endAt;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

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
    issuedQuantity += 1;
  }

  public void validateIssuableAt(LocalDateTime now) {
    if (now.isBefore(startAt) || now.isAfter(endAt)) {
      throw new BusinessException(ErrorCode.ISSUE_PERIOD_CLOSED);
    }
  }

  public boolean isIssuableAt(LocalDateTime now) {
    return !now.isBefore(startAt) && !now.isAfter(endAt);
  }

  public boolean isSoldOut() {
    return issuedQuantity >= totalQuantity;
  }

  public int remainingQuantity() {
    return Math.max(totalQuantity - issuedQuantity, 0);
  }

  public CouponEventStatus statusAt(LocalDateTime now) {
    return CouponEventStatus.from(this, now);
  }

  @PrePersist
  void prePersist() {
    LocalDateTime now = LocalDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
    this.version = 0L;
  }

  @PreUpdate
  void preUpdate() {
    this.updatedAt = LocalDateTime.now();
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
    if (!startAt.isBefore(endAt)) {
      throw new IllegalArgumentException("발급 종료 시각은 시작 시각보다 뒤여야 합니다.");
    }
  }
}
