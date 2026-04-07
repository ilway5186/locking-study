package com.ilway.reservation.reservation.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReservationGroupRequestRepository extends JpaRepository<ReservationGroupRequest, Long> {

  Optional<ReservationGroupRequest> findByActionAndUserIdAndIdempotencyKey(
      ReservationRequestAction action,
      Long userId,
      String idempotencyKey
  );

  long countByShowId(Long showId);

  long countByShowIdAndRequestStatus(Long showId, ReservationRequestStatus requestStatus);

  @Query("select coalesce(sum(request.reusedCount), 0) from ReservationGroupRequest request where request.showId = :showId")
  long sumReusedCountByShowId(Long showId);

  @Query("""
      select request.failureReason as failureReason, count(request) as count
      from ReservationGroupRequest request
      where request.showId = :showId
        and request.requestStatus = com.ilway.reservation.reservation.domain.ReservationRequestStatus.FAILED
      group by request.failureReason
      """)
  List<FailureReasonCountProjection> countFailureReasonsByShowId(Long showId);

  long countByShowIdAndFailureReasonIn(Long showId, Collection<ReservationFailureReason> failureReasons);
}
