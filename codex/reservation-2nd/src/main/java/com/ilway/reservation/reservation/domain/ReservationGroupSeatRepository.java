package com.ilway.reservation.reservation.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationGroupSeatRepository extends JpaRepository<ReservationGroupSeat, Long> {

  @Query("""
      select seat
      from ReservationGroupSeat seat
      join fetch seat.reservationGroup groupEntity
      where seat.seatId in :seatIds
      order by seat.seatId asc, seat.createdAt desc, seat.id desc
      """)
  List<ReservationGroupSeat> findAllWithGroupBySeatIdInOrderBySeatIdAscCreatedAtDescIdDesc(List<Long> seatIds);

  @Query("""
      select seat
      from ReservationGroupSeat seat
      join fetch seat.reservationGroup groupEntity
      where groupEntity.showId = :showId
      order by seat.seatId asc, seat.createdAt desc, seat.id desc
      """)
  List<ReservationGroupSeat> findAllWithGroupByShowIdOrderBySeatIdAscCreatedAtDescIdDesc(Long showId);
}
