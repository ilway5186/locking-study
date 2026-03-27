package com.ilway.couponsystem.coupon.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Getter
@Accessors(fluent = true)
@Entity
@Table(name = "coupon_event")
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

}
