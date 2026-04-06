package com.ilway.reservationsystem.reservation.domain.repository;

import com.ilway.reservationsystem.reservation.domain.ReservationRequestAction;
import com.ilway.reservationsystem.reservation.domain.ReservationRequestStatus;
import com.ilway.reservationsystem.reservation.domain.SeatReservationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeatReservationRequestRepository extends JpaRepository<SeatReservationRequest, Long> {

  Optional<SeatReservationRequest> findByActionAndUserIdAndIdempotencyKey(
    ReservationRequestAction action,
    Long userId,
    String idempotencyKey
  );

  long countByShowId(Long showId);

  long countByShowIdAndRequestStatus(Long showId, ReservationRequestStatus requestStatus);

  @Query("SELECT r.failureReason, COUNT(r) FROM SeatReservationRequest r WHERE r.showId = :showId AND r.failureReason IS NOT NULL GROUP BY r.failureReason")
  List<FailureReasonCountProjection> countFailureReasonsByShowId(Long showId);

  @Query("SELECT COALESCE(SUM(r.reusedCount), 0) FROM SeatReservationRequest r WHERE r.showId = :showId")
  Long sumReusedCountByShowId(Long showId);

}
