package com.ilway.coupon.coupon.event;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CouponEventRepository extends JpaRepository<CouponEvent, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select couponEvent from CouponEvent couponEvent where couponEvent.id = :couponEventId")
  Optional<CouponEvent> findByIdForUpdate(@Param("couponEventId") Long couponEventId);
}
