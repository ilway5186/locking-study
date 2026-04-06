package com.ilway.reservationsystem.reservation.domain.repository;

import com.ilway.reservationsystem.reservation.domain.SeatReservation;
import com.ilway.reservationsystem.reservation.domain.SeatReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

  Optional<SeatReservation> findTopBySeatIdOrderByCreatedAtDescIdDesc(Long seatId);

  List<SeatReservation> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<SeatReservation> findByStatusAndHoldExpiresAtLessThanEqual(SeatReservationStatus status, Instant holdExpiresAt);

  @Query("SELECT r FROM SeatReservation r WHERE r.showId = :showId ORDER BY r.seatID ASC, r.createdAt DESC")
  List<SeatReservation> findAllByShowIdOrderBySeatIdAndCreatedAtDesc(Long showId);

  @Query("SELECT r.status, COUNT(r) FROM SeatReservation r WHERE r.showId = :showId GROUP BY r.status")
  List<ReservationStatusCountProjection> countByShowIdGroupByStatus(Long showId);

}
