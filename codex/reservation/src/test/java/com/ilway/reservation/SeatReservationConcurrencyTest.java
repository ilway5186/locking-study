package com.ilway.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ilway.reservation.admin.application.AdminStatisticsService;
import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.api.dto.HoldSeatResponse;
import com.ilway.reservation.reservation.application.HoldSeatCommand;
import com.ilway.reservation.reservation.application.SeatReservationFacade;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.SeatReservationRequestRepository;
import com.ilway.reservation.reservation.domain.SeatReservationRepository;
import com.ilway.reservation.reservation.domain.SeatReservationStatus;
import com.ilway.reservation.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservation.seat.application.SeatService;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.api.dto.CreateShowRequest;
import com.ilway.reservation.show.api.dto.ShowResponse;
import com.ilway.reservation.show.application.ShowService;
import com.ilway.reservation.show.domain.ShowRepository;
import com.ilway.reservation.support.MutableClock;
import com.ilway.reservation.support.MutableClockTestConfiguration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({TestcontainersConfiguration.class, MutableClockTestConfiguration.class})
@SpringBootTest
class SeatReservationConcurrencyTest {

  @Autowired
  private ShowService showService;

  @Autowired
  private SeatService seatService;

  @Autowired
  private SeatReservationFacade reservationFacade;

  @Autowired
  private AdminStatisticsService adminStatisticsService;

  @Autowired
  private MutableClock mutableClock;

  @Autowired
  private SeatReservationRequestRepository requestRepository;

  @Autowired
  private SeatReservationRepository reservationRepository;

  @Autowired
  private SeatRepository seatRepository;

  @Autowired
  private ShowRepository showRepository;

  private ExecutorService executorService;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    reservationRepository.deleteAll();
    seatRepository.deleteAll();
    showRepository.deleteAll();
    mutableClock.setInstant(Instant.parse("2026-03-31T00:00:00Z"));
    executorService = Executors.newFixedThreadPool(32);
  }

  @AfterEach
  void tearDown() {
    executorService.shutdownNow();
  }

  @Test
  void 같은_seatId에_100개_동시_hold를_보내면_정확히_1개만_성공한다() throws Exception {
    Fixture fixture = createFixture(1);
    long seatId = fixture.seatIds().getFirst();

    Set<Long> successReservationIds = ConcurrentHashMap.newKeySet();
    Set<ReservationFailureReason> failureReasons = ConcurrentHashMap.newKeySet();

    runConcurrent(100, index -> {
      try {
        HoldSeatResponse response = reservationFacade.hold(
            new HoldSeatCommand("same-seat-" + index, fixture.showId(), seatId, (long) index));
        successReservationIds.add(response.reservation().reservationId());
      } catch (ReservationException exception) {
        failureReasons.add(exception.getReason());
      }
    });

    assertEquals(1, successReservationIds.size());
    assertTrue(failureReasons.contains(ReservationFailureReason.SEAT_ALREADY_HELD));

    var stats = adminStatisticsService.getShowStatistics(fixture.showId());
    assertEquals(100, stats.requestCount());
    assertEquals(99, stats.failedRequestCount());
    assertEquals(99L, stats.failureReasonCounts().get("SEAT_ALREADY_HELD"));
  }

  @Test
  void 서로_다른_seatId는_병렬로_hold할_수_있다() throws Exception {
    Fixture fixture = createFixture(20);
    Set<Long> successReservationIds = ConcurrentHashMap.newKeySet();

    runConcurrent(20, index -> {
      HoldSeatResponse response = reservationFacade.hold(
          new HoldSeatCommand("seat-" + index, fixture.showId(), fixture.seatIds().get(index), 1000L + index));
      successReservationIds.add(response.reservation().reservationId());
    });

    assertEquals(20, successReservationIds.size());
    assertEquals(20, reservationRepository.findAll().size());
  }

  @Test
  void 같은_userId와_같은_idempotencyKey_재요청은_결과를_재사용한다() {
    Fixture fixture = createFixture(1);
    long seatId = fixture.seatIds().getFirst();

    HoldSeatResponse first = reservationFacade.hold(new HoldSeatCommand("idem-1", fixture.showId(), seatId, 1L));
    HoldSeatResponse second = reservationFacade.hold(new HoldSeatCommand("idem-1", fixture.showId(), seatId, 1L));

    assertEquals(first.reservation().reservationId(), second.reservation().reservationId());
    assertTrue(second.reused());
  }

  @Test
  void hold_만료_직전에는_확정되고_직후에는_실패한다() {
    Fixture fixture = createFixture(2);

    HoldSeatResponse first = reservationFacade.hold(new HoldSeatCommand("before-expire", fixture.showId(), fixture.seatIds().get(0), 1L));
    mutableClock.advanceSeconds(1);
    var confirmed = reservationFacade.confirm(first.reservation().reservationId(), 1L);
    assertEquals(SeatReservationStatus.RESERVED, confirmed.status());

    HoldSeatResponse second = reservationFacade.hold(new HoldSeatCommand("after-expire", fixture.showId(), fixture.seatIds().get(1), 2L));
    mutableClock.advanceSeconds(2);

    try {
      reservationFacade.confirm(second.reservation().reservationId(), 2L);
    } catch (ReservationException exception) {
      assertEquals(ReservationFailureReason.HOLD_EXPIRED, exception.getReason());
    }
  }

  @Test
  void 이미_reserved된_좌석에_대한_동시_hold는_모두_실패한다() throws Exception {
    Fixture fixture = createFixture(1);
    long seatId = fixture.seatIds().getFirst();

    HoldSeatResponse first = reservationFacade.hold(new HoldSeatCommand("reserve-first", fixture.showId(), seatId, 1L));
    reservationFacade.confirm(first.reservation().reservationId(), 1L);

    Set<ReservationFailureReason> failureReasons = ConcurrentHashMap.newKeySet();

    runConcurrent(20, index -> {
      try {
        reservationFacade.hold(new HoldSeatCommand("reserved-seat-" + index, fixture.showId(), seatId, 10L + index));
      } catch (ReservationException exception) {
        failureReasons.add(exception.getReason());
      }
    });

    assertEquals(Set.of(ReservationFailureReason.SEAT_ALREADY_RESERVED), failureReasons);
  }

  private Fixture createFixture(int seatCount) {
    ShowResponse show = showService.createShow(new CreateShowRequest(
        "concert",
        Instant.parse("2026-04-01T10:00:00Z"),
        Instant.parse("2026-04-01T12:00:00Z"),
        Instant.parse("2026-03-30T00:00:00Z"),
        Instant.parse("2026-04-01T09:00:00Z")
    ));

    List<String> seatNumbers = new ArrayList<>();
    for (int index = 1; index <= seatCount; index++) {
      seatNumbers.add("A" + index);
    }

    List<Long> seatIds = seatService.createSeats(new CreateSeatsRequest(show.showId(), seatNumbers)).stream()
        .map(seat -> seat.seatId())
        .toList();

    return new Fixture(show.showId(), seatIds);
  }

  private void runConcurrent(int count, Task task) throws Exception {
    CountDownLatch ready = new CountDownLatch(count);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(count);

    for (int index = 0; index < count; index++) {
      final int currentIndex = index;
      executorService.submit(() -> {
        ready.countDown();
        try {
          start.await();
          task.run(currentIndex);
        } catch (Exception ignored) {
        } finally {
          done.countDown();
        }
      });
    }

    ready.await(5, TimeUnit.SECONDS);
    start.countDown();
    done.await(20, TimeUnit.SECONDS);
  }

  private record Fixture(Long showId, List<Long> seatIds) {
  }

  @FunctionalInterface
  private interface Task {
    void run(int index) throws Exception;
  }
}
