package com.ilway.reservation.seat.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SeatRepository extends JpaRepository<Seat, Long> {

  List<Seat> findByShowIdOrderBySeatNumberAsc(Long showId);

  boolean existsByShowIdAndSeatNumber(Long showId, String seatNumber);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Seat s where s.id = :seatId and s.showId = :showId")
  Optional<Seat> findByIdAndShowIdForUpdate(Long seatId, Long showId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from Seat s where s.showId = :showId and s.id in :seatIds order by s.id asc")
  List<Seat> findAllByShowIdAndIdInOrderByIdAscForUpdate(Long showId, List<Long> seatIds);
}
