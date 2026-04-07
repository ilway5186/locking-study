package com.ilway.reservation.reservation.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReservationGroupRepository extends JpaRepository<ReservationGroup, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select g from ReservationGroup g where g.id = :groupId")
  Optional<ReservationGroup> findByIdForUpdate(Long groupId);

  @EntityGraph(attributePaths = "seats")
  @Query("select g from ReservationGroup g where g.id = :id")
  Optional<ReservationGroup> findByIdWithSeats(Long id);

  @EntityGraph(attributePaths = "seats")
  List<ReservationGroup> findByUserIdOrderByCreatedAtDesc(Long userId);

  List<ReservationGroup> findByStatusAndHoldExpiresAtLessThanEqual(ReservationGroupStatus status, Instant holdExpiresAt);

  @Query("select g.status as status, count(g) as count from ReservationGroup g where g.showId = :showId group by g.status")
  List<GroupStatusCountProjection> countByShowIdGroupByStatus(Long showId);
}
