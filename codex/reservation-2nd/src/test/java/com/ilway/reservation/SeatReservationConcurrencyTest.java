package com.ilway.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ilway.reservation.common.exception.ReservationException;
import com.ilway.reservation.reservation.api.dto.HoldReservationGroupResponse;
import com.ilway.reservation.reservation.application.HoldReservationGroupCommand;
import com.ilway.reservation.reservation.application.ReservationGroupFacade;
import com.ilway.reservation.reservation.domain.ReservationFailureReason;
import com.ilway.reservation.reservation.domain.ReservationGroupRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupRequestRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupSeatRepository;
import com.ilway.reservation.seat.api.dto.CreateSeatsRequest;
import com.ilway.reservation.seat.application.SeatService;
import com.ilway.reservation.show.api.dto.CreateShowRequest;
import com.ilway.reservation.show.api.dto.ShowResponse;
import com.ilway.reservation.show.application.ShowService;
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
  private ReservationGroupFacade reservationGroupFacade;

  @Autowired
  private MutableClock mutableClock;

  @Autowired
  private ReservationGroupRequestRepository requestRepository;

  @Autowired
  private ReservationGroupRepository reservationGroupRepository;

  @Autowired
  private ReservationGroupSeatRepository reservationGroupSeatRepository;

  @Autowired
  private com.ilway.reservation.seat.domain.SeatRepository seatRepository;

  @Autowired
  private com.ilway.reservation.show.domain.ShowRepository showRepository;

  private ExecutorService executorService;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    reservationGroupSeatRepository.deleteAll();
    reservationGroupRepository.deleteAll();
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
  void 같은_좌석_조합에_동시_hold를_보내면_정확히_1개만_성공한다() throws Exception {
    Fixture fixture = createFixture(3);
    List<Long> sameSelection = fixture.seatIds().subList(0, 3);

    Set<Long> successGroupIds = ConcurrentHashMap.newKeySet();
    Set<ReservationFailureReason> failureReasons = ConcurrentHashMap.newKeySet();

    runConcurrent(40, index -> {
      try {
        HoldReservationGroupResponse response = reservationGroupFacade.hold(
            new HoldReservationGroupCommand("same-selection-" + index, fixture.showId(), (long) index, sameSelection));
        successGroupIds.add(response.reservationGroup().reservationGroupId());
      } catch (ReservationException exception) {
        failureReasons.add(exception.getReason());
      }
    });

    assertEquals(1, successGroupIds.size());
    assertTrue(failureReasons.contains(ReservationFailureReason.SEAT_ALREADY_HELD));
    assertEquals(1, reservationGroupRepository.findAll().size());
  }

  @Test
  void 일부_겹치는_좌석_조합은_하나만_성공하고_부분_성공은_남지_않는다() throws Exception {
    Fixture fixture = createFixture(5);
    Set<Long> successGroupIds = ConcurrentHashMap.newKeySet();
    Set<ReservationFailureReason> failureReasons = ConcurrentHashMap.newKeySet();

    runConcurrent(2, index -> {
      List<Long> selection = index == 0
          ? List.of(fixture.seatIds().get(0), fixture.seatIds().get(1), fixture.seatIds().get(2))
          : List.of(fixture.seatIds().get(2), fixture.seatIds().get(3), fixture.seatIds().get(4));
      try {
        HoldReservationGroupResponse response = reservationGroupFacade.hold(
            new HoldReservationGroupCommand("overlap-" + index, fixture.showId(), 100L + index, selection));
        successGroupIds.add(response.reservationGroup().reservationGroupId());
      } catch (ReservationException exception) {
        failureReasons.add(exception.getReason());
      }
    });

    assertEquals(1, successGroupIds.size());
    assertTrue(failureReasons.contains(ReservationFailureReason.SEAT_ALREADY_HELD));
    assertEquals(1, reservationGroupRepository.findAll().size());
  }

  @Test
  void 서로_다른_좌석_조합은_병렬로_hold할_수_있다() throws Exception {
    Fixture fixture = createFixture(6);
    Set<Long> successGroupIds = ConcurrentHashMap.newKeySet();

    List<List<Long>> selections = List.of(
        List.of(fixture.seatIds().get(0), fixture.seatIds().get(1)),
        List.of(fixture.seatIds().get(2), fixture.seatIds().get(3)),
        List.of(fixture.seatIds().get(4), fixture.seatIds().get(5))
    );

    runConcurrent(3, index -> {
      HoldReservationGroupResponse response = reservationGroupFacade.hold(
          new HoldReservationGroupCommand("disjoint-" + index, fixture.showId(), 300L + index, selections.get(index)));
      successGroupIds.add(response.reservationGroup().reservationGroupId());
    });

    assertEquals(3, successGroupIds.size());
    assertEquals(3, reservationGroupRepository.findAll().size());
  }

  @Test
  void 동일한_그룹_요청과_같은_idempotencyKey_동시_재시도는_결과를_재사용한다() throws Exception {
    Fixture fixture = createFixture(3);
    List<Long> selection = List.of(fixture.seatIds().get(0), fixture.seatIds().get(1), fixture.seatIds().get(2));
    Set<Long> groupIds = ConcurrentHashMap.newKeySet();
    Set<Boolean> reusedFlags = ConcurrentHashMap.newKeySet();
    Set<ReservationFailureReason> failureReasons = ConcurrentHashMap.newKeySet();

    CountDownLatch ready = new CountDownLatch(10);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(10);

    for (int index = 0; index < 10; index++) {
      executorService.submit(() -> {
        ready.countDown();
        try {
          start.await();
          HoldReservationGroupResponse response = reservationGroupFacade.hold(
              new HoldReservationGroupCommand("same-key", fixture.showId(), 77L, selection));
          groupIds.add(response.reservationGroup().reservationGroupId());
          reusedFlags.add(response.reused());
        } catch (ReservationException exception) {
          failureReasons.add(exception.getReason());
        } catch (Exception exception) {
          throw new RuntimeException(exception);
        } finally {
          done.countDown();
        }
      });
    }

    ready.await(5, TimeUnit.SECONDS);
    start.countDown();
    done.await(20, TimeUnit.SECONDS);

    assertEquals(1, groupIds.size());
    assertTrue(failureReasons.isEmpty());
    assertTrue(reusedFlags.contains(true));
    assertEquals(1, reservationGroupRepository.findAll().size());
  }

  @Test
  void lock_ordering을_위해_좌석은_항상_정렬된_순서로_잠긴다() {
    // 실제 데드락은 비결정적이라 테스트로 재현하기보다,
    // 2차 구현에서는 seat selection 자체를 정렬해 [3,2,1]과 [1,2,3]이 같은 락 순서를 따르게 강제한다.
    Fixture fixture = createFixture(3);
    HoldReservationGroupResponse response = reservationGroupFacade.hold(
        new HoldReservationGroupCommand(
            "ordered",
            fixture.showId(),
            1L,
            List.of(fixture.seatIds().get(2), fixture.seatIds().get(0), fixture.seatIds().get(1))
        )
    );

    assertEquals(List.of("A1", "A2", "A3"),
        response.reservationGroup().seats().stream().map(seat -> seat.seatNumber()).toList());
  }

  @Test
  void 만료된_그룹은_확정에_실패한다() {
    Fixture fixture = createFixture(2);

    HoldReservationGroupResponse hold = reservationGroupFacade.hold(
        new HoldReservationGroupCommand("expire", fixture.showId(), 11L, List.of(fixture.seatIds().get(0), fixture.seatIds().get(1))));
    mutableClock.advanceSeconds(3);

    ReservationException exception = org.junit.jupiter.api.Assertions.assertThrows(ReservationException.class, () ->
        reservationGroupFacade.confirm(hold.reservationGroup().reservationGroupId(), 11L));

    assertEquals(ReservationFailureReason.HOLD_EXPIRED, exception.getReason());
    assertFalse(reservationGroupRepository.findAll().isEmpty());
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
