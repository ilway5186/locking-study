package com.ilway.reservation.reservation.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeatReservationRequestRepository extends JpaRepository<SeatReservationRequest, Long> {

  Optional<SeatReservationRequest> findByActionAndUserIdAndIdempotencyKey(
      ReservationRequestAction action,
      Long userId,
      String idempotencyKey
  );

  long countByShowId(Long showId);

  long countByShowIdAndRequestStatus(Long showId, ReservationRequestStatus requestStatus);

  @Query("select r.failureReason, count(r) from SeatReservationRequest r where r.showId = :showId and r.failureReason is not null group by r.failureReason")
  List<Object[]> countFailureReasonsByShowId(Long showId);

  @Query("select coalesce(sum(r.reusedCount), 0) from SeatReservationRequest r where r.showId = :showId")
  Long sumReusedCountByShowId(Long showId);
}
