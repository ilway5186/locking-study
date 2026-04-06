package com.ilway.reservationsystem.seat.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

  List<Seat> findByShowIdOrderBySeatNumberAsc(Long showId);

  boolean existsByShowIdAndSeatNumber(Long shwId, String seatNumber);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Seat s WHERE s.id = :seatId AND s.showId = :showId")
  Optional<Seat> findByIdAndShowIdForUpdate(Long seatId, Long showId);

}
