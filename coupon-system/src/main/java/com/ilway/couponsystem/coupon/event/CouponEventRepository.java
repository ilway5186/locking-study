package com.ilway.couponsystem.coupon.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponEventRepository extends JpaRepository<CouponEvent, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT e FROM CouponEvent e WHERE e.id = :couponEventId")
  Optional<CouponEvent> findByIdForUpdate(@Param("couponEventId") Long couponEventId);

}
