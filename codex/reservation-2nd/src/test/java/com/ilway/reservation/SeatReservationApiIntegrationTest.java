package com.ilway.reservation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ilway.reservation.reservation.domain.SeatReservationRequestRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupRequestRepository;
import com.ilway.reservation.reservation.domain.ReservationGroupSeatRepository;
import com.ilway.reservation.seat.domain.SeatRepository;
import com.ilway.reservation.show.domain.ShowRepository;
import com.ilway.reservation.support.MutableClock;
import com.ilway.reservation.support.MutableClockTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import({TestcontainersConfiguration.class, MutableClockTestConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class SeatReservationApiIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MutableClock mutableClock;

  @Autowired
  private ReservationGroupRequestRepository requestRepository;

  @Autowired
  private ReservationGroupRepository reservationGroupRepository;

  @Autowired
  private ReservationGroupSeatRepository reservationGroupSeatRepository;

  @Autowired
  private SeatRepository seatRepository;

  @Autowired
  private ShowRepository showRepository;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    reservationGroupSeatRepository.deleteAll();
    reservationGroupRepository.deleteAll();
    seatRepository.deleteAll();
    showRepository.deleteAll();
    mutableClock.setInstant(java.time.Instant.parse("2026-03-31T00:00:00Z"));
  }

  @Test
  void 다중_좌석_hold_재사용_확정_취소_통계_조회가_동작한다() throws Exception {
    long showId = createShow();
    JsonNode seats = createSeats(showId, "A1", "A2", "A3", "A4");
    long seatA1 = seats.get(0).get("seatId").asLong();
    long seatA2 = seats.get(1).get("seatId").asLong();
    long seatA3 = seats.get(2).get("seatId").asLong();
    long seatA4 = seats.get(3).get("seatId").asLong();

    mockMvc.perform(get("/api/seats/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

    MvcResult firstHold = mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-a1-user1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatA1, seatA2)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reused").value(false))
        .andExpect(jsonPath("$.reservationGroup.status").value("HOLD"))
        .andExpect(jsonPath("$.reservationGroup.seats.length()").value(2))
        .andReturn();

    long groupId1 = read(firstHold).at("/reservationGroup/reservationGroupId").asLong();

    mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-a1-user1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatA2, seatA1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reused").value(true))
        .andExpect(jsonPath("$.reservationGroup.reservationGroupId").value(groupId1));

    mockMvc.perform(post("/api/reservation-groups/{groupId}/confirm", groupId1)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESERVED"));

    MvcResult secondHold = mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-a34-user1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatA3, seatA4)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reservationGroup.status").value("HOLD"))
        .andReturn();

    long groupId2 = read(secondHold).at("/reservationGroup/reservationGroupId").asLong();

    mockMvc.perform(post("/api/reservation-groups/{groupId}/cancel", groupId2)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-a1-user2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 2,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatA1, seatA2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));

    mockMvc.perform(get("/api/reservation-groups/{groupId}", groupId1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESERVED"));

    mockMvc.perform(get("/api/users/{userId}/reservation-groups", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("CANCELLED"))
        .andExpect(jsonPath("$[1].status").value("RESERVED"));

    mockMvc.perform(get("/api/admin/statistics/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSeatCount").value(4))
        .andExpect(jsonPath("$.totalGroupRequestCount").value(3))
        .andExpect(jsonPath("$.successfulHoldGroupCount").value(2))
        .andExpect(jsonPath("$.failedGroupRequestCount").value(1))
        .andExpect(jsonPath("$.idempotentReuseCount").value(1))
        .andExpect(jsonPath("$.seatConflictFailureCount").value(1))
        .andExpect(jsonPath("$.groupStatusCounts.RESERVED").value(1))
        .andExpect(jsonPath("$.groupStatusCounts.CANCELLED").value(1))
        .andExpect(jsonPath("$.failureReasonCounts.SEAT_ALREADY_RESERVED").value(1));
  }

  @Test
  void 일부_좌석이_충돌하면_전체가_실패하고_부분_성공이_남지_않는다() throws Exception {
    long showId = createShow();
    JsonNode seats = createSeats(showId, "A1", "A2", "A3");
    long seatA1 = seats.get(0).get("seatId").asLong();
    long seatA2 = seats.get(1).get("seatId").asLong();
    long seatA3 = seats.get(2).get("seatId").asLong();

    mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-a12")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatA1, seatA2)))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-a23")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 2,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatA2, seatA3)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEAT_ALREADY_HELD"));

    mockMvc.perform(get("/api/seats/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[2].status").value("AVAILABLE"));
  }

  @Test
  void hold가_만료되면_그룹_확정에_실패하고_좌석은_available로_돌아온다() throws Exception {
    long showId = createShow();
    JsonNode seats = createSeats(showId, "A1", "A2");
    long seatId = seats.get(0).get("seatId").asLong();
    long seatId2 = seats.get(1).get("seatId").asLong();

    MvcResult hold = mockMvc.perform(post("/api/shows/{showId}/reservation-groups/hold", showId)
            .header("Idempotency-Key", "hold-expire")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 10,
                  "seatIds": [%d, %d]
                }
                """.formatted(seatId, seatId2)))
        .andExpect(status().isCreated())
        .andReturn();

    long groupId = read(hold).at("/reservationGroup/reservationGroupId").asLong();
    mutableClock.advanceSeconds(3);

    mockMvc.perform(post("/api/reservation-groups/{groupId}/confirm", groupId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 10
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

    mockMvc.perform(get("/api/seats/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
        .andExpect(jsonPath("$[1].status").value("AVAILABLE"));

    mockMvc.perform(get("/api/reservation-groups/{groupId}", groupId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"));

    mockMvc.perform(get("/api/admin/statistics/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groupStatusCounts.EXPIRED").value(1));
  }

  private long createShow() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/shows")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "spring concert",
                  "startAt": "2026-04-01T10:00:00Z",
                  "endAt": "2026-04-01T12:00:00Z",
                  "bookingOpenAt": "2026-03-30T00:00:00Z",
                  "bookingCloseAt": "2026-04-01T09:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn();
    return read(result).get("showId").asLong();
  }

  private JsonNode createSeats(long showId, String... seatNumbers) throws Exception {
    String seatArray = java.util.Arrays.stream(seatNumbers)
        .map(value -> "\"%s\"".formatted(value))
        .collect(java.util.stream.Collectors.joining(","));

    MvcResult result = mockMvc.perform(post("/api/seats")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "showId": %d,
                  "seatNumbers": [%s]
                }
                """.formatted(showId, seatArray)))
        .andExpect(status().isCreated())
        .andReturn();
    return read(result);
  }

  private JsonNode read(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }
}
