package com.ilway.reservation.reservation.application;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.api.dto.ReservationGroupResponse;
import com.ilway.reservation.reservation.api.dto.ReservationGroupSeatResponse;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationGroup;
import com.ilway.reservation.reservation.domain.ReservationGroupRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupSeat;
import com.ilway.reservation.reservation.domain.ReservationGroupSeatRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupStatus;
import com.ilway.reservation.seat.domain.Seat;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.Show;
import com.ilway.reservation.show.domain.ShowRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationGroupTxService {

  private final ShowRepository showRepository;
  private final SeatRepository seatRepository;
  private final ReservationGroupRepository reservationGroupRepository;
  private final ReservationGroupSeatRepository reservationGroupSeatRepository;
  private final BookingWindowPolicy bookingWindowPolicy;
  private final Clock clock;
  private final Duration holdDuration;

  public ReservationGroupTxService(
      ShowRepository showRepository,
      SeatRepository seatRepository,
      ReservationGroupRepository reservationGroupRepository,
      ReservationGroupSeatRepository reservationGroupSeatRepository,
      BookingWindowPolicy bookingWindowPolicy,
      Clock clock,
      @Value("${reservation.hold.duration:PT5M}") Duration holdDuration
  ) {
    this.showRepository = showRepository;
    this.seatRepository = seatRepository;
    this.reservationGroupRepository = reservationGroupRepository;
    this.reservationGroupSeatRepository = reservationGroupSeatRepository;
    this.bookingWindowPolicy = bookingWindowPolicy;
    this.clock = clock;
    this.holdDuration = holdDuration;
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationGroupResponse processHold(Long showId, Long userId, List<Long> normalizedSeatIds) {
    Instant now = Instant.now(clock);
    Show show = showRepository.findById(showId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.SHOW_NOT_FOUND));
    bookingWindowPolicy.validateHoldable(show, now);

    // Multi-seat hold must acquire the same seat rows in the same order to reduce deadlock risk.
    List<Seat> lockedSeats = seatRepository.findAllByShowIdAndIdInOrderByIdAscForUpdate(showId, normalizedSeatIds);
    if (lockedSeats.size() != normalizedSeatIds.size()) {
      throw new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND);
    }

    Map<Long, ReservationGroupSeat> latestSeatMap = latestSeatMap(normalizedSeatIds);
    expireOverdueGroups(latestSeatMap.values(), now);
    rejectIfOccupied(latestSeatMap.values(), now);

    ReservationGroup group = reservationGroupRepository.save(
        ReservationGroup.hold(showId, userId, now.plus(holdDuration), lockedSeats)
    );
    return toResponse(group);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationGroupResponse confirm(Long groupId, Long userId) {
    Instant now = Instant.now(clock);
    ReservationGroup group = reservationGroupRepository.findByIdForUpdate(groupId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.GROUP_NOT_FOUND));
    group.getSeats().size();

    lockGroupSeats(group);
    if (group.isExpiredAt(now)) {
      group.expire(now);
      throw new ReservationException(ReservationFailureReason.HOLD_EXPIRED);
    }

    // PAYMENT_PENDING is modeled explicitly so the future PG boundary is visible in the state machine.
    group.moveToPaymentPending(userId, now);
    group.reserve(userId, now);
    return toResponse(group);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public ReservationGroupResponse cancel(Long groupId, Long userId) {
    Instant now = Instant.now(clock);
    ReservationGroup group = reservationGroupRepository.findByIdForUpdate(groupId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.GROUP_NOT_FOUND));
    group.getSeats().size();

    lockGroupSeats(group);
    group.cancel(userId, now);
    return toResponse(group);
  }

  @Transactional(noRollbackFor = ReservationException.class, isolation = Isolation.READ_COMMITTED)
  public boolean expireReservationGroupIfNeeded(Long groupId) {
    ReservationGroup group = reservationGroupRepository.findByIdForUpdate(groupId).orElse(null);
    if (group == null) {
      return false;
    }

    group.getSeats().size();
    if (!group.isPendingCompletion()) {
      return false;
    }

    lockGroupSeats(group);
    Instant now = Instant.now(clock);
    if (!group.isExpiredAt(now)) {
      return false;
    }
    group.expire(now);
    return true;
  }

  @Transactional(readOnly = true)
  public ReservationGroupResponse getGroup(Long groupId) {
    ReservationGroup group = reservationGroupRepository.findByIdWithSeats(groupId)
        .orElseThrow(() -> new ReservationException(ReservationFailureReason.GROUP_NOT_FOUND));
    return toResponse(group);
  }

  @Transactional(readOnly = true)
  public List<ReservationGroupResponse> getUserGroups(Long userId) {
    return reservationGroupRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  public ReservationGroupResponse toResponse(ReservationGroup group) {
    List<ReservationGroupSeatResponse> seats = group.getSeats().stream()
        .sorted(Comparator.comparing(ReservationGroupSeat::getSeatId))
        .map(seat -> new ReservationGroupSeatResponse(seat.getSeatId(), seat.getSeatNumber()))
        .toList();

    return new ReservationGroupResponse(
        group.getId(),
        group.getShowId(),
        group.getUserId(),
        group.getStatus(),
        group.getHoldExpiresAt(),
        group.getPaymentPendingAt(),
        group.getReservedAt(),
        group.getCancelledAt(),
        group.getExpiredAt(),
        seats
    );
  }

  private void lockGroupSeats(ReservationGroup group) {
    List<Long> seatIds = group.getSeats().stream()
        .map(ReservationGroupSeat::getSeatId)
        .sorted()
        .toList();
    List<Seat> lockedSeats = seatRepository.findAllByShowIdAndIdInOrderByIdAscForUpdate(group.getShowId(), seatIds);
    if (lockedSeats.size() != seatIds.size()) {
      throw new ReservationException(ReservationFailureReason.SEAT_NOT_FOUND);
    }
  }

  private Map<Long, ReservationGroupSeat> latestSeatMap(List<Long> seatIds) {
    Map<Long, ReservationGroupSeat> latestSeatMap = new LinkedHashMap<>();
    for (ReservationGroupSeat reservationGroupSeat
        : reservationGroupSeatRepository.findAllWithGroupBySeatIdInOrderBySeatIdAscCreatedAtDescIdDesc(seatIds)) {
      latestSeatMap.putIfAbsent(reservationGroupSeat.getSeatId(), reservationGroupSeat);
    }
    return latestSeatMap;
  }

  private void expireOverdueGroups(Collection<ReservationGroupSeat> latestSeats, Instant now) {
    Set<Long> expiredGroupIds = latestSeats.stream()
        .map(ReservationGroupSeat::getReservationGroup)
        .filter(Objects::nonNull)
        .filter(group -> group.isExpiredAt(now))
        .map(ReservationGroup::getId)
        .collect(Collectors.toSet());

    latestSeats.stream()
        .map(ReservationGroupSeat::getReservationGroup)
        .filter(Objects::nonNull)
        .filter(group -> expiredGroupIds.contains(group.getId()))
        .forEach(group -> group.expire(now));
  }

  private void rejectIfOccupied(Collection<ReservationGroupSeat> latestSeats, Instant now) {
    for (ReservationGroupSeat latestSeat : latestSeats) {
      ReservationGroup group = latestSeat.getReservationGroup();
      if (group == null) {
        continue;
      }

      if (group.getStatus() == ReservationGroupStatus.HOLD && !group.isExpiredAt(now)) {
        throw new ReservationException(ReservationFailureReason.SEAT_ALREADY_HELD);
      }
      if (group.getStatus() == ReservationGroupStatus.PAYMENT_PENDING && !group.isExpiredAt(now)) {
        throw new ReservationException(ReservationFailureReason.SEAT_PAYMENT_PENDING);
      }
      if (group.getStatus() == ReservationGroupStatus.RESERVED) {
        throw new ReservationException(ReservationFailureReason.SEAT_ALREADY_RESERVED);
      }
    }
  }
}
