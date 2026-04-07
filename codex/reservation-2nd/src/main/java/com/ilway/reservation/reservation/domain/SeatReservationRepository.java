package com.ilway.reservation.reservation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

  Optional<SeatReservation> findTopBySeatIdOrderByCreatedAtDescIdDesc(Long seatId);

  List<SeatReservation> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<SeatReservation> findByStatusAndHoldExpiresAtLessThanEqual(SeatReservationStatus status, Instant holdExpiresAt);

  @Query("select r from SeatReservation r where r.showId = :showId order by r.seatId asc, r.createdAt desc")
  List<SeatReservation> findAllByShowIdOrderBySeatIdAndCreatedAtDesc(Long showId);

  @Query("select r.status as status, count(r) as count from SeatReservation r where r.showId = :showId group by r.status")
  List<ReservationStatusCountProjection> countByShowIdGroupByStatus(Long showId);
}
