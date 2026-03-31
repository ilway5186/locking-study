package com.ilway.reservation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ilway.reservation.reservation.domain.SeatReservationRequestRepository;
import com.ilway.reservation.reservation.domain.SeatReservationRepository;
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
  private SeatReservationRequestRepository requestRepository;

  @Autowired
  private SeatReservationRepository reservationRepository;

  @Autowired
  private SeatRepository seatRepository;

  @Autowired
  private ShowRepository showRepository;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    reservationRepository.deleteAll();
    seatRepository.deleteAll();
    showRepository.deleteAll();
    mutableClock.setInstant(java.time.Instant.parse("2026-03-31T00:00:00Z"));
  }

  @Test
  void 공연_좌석_hold_재사용_확정_취소_통계_조회가_동작한다() throws Exception {
    long showId = createShow();
    JsonNode seats = createSeats(showId, "A1", "A2", "A3");
    long seatA1 = seats.get(0).get("seatId").asLong();
    long seatA2 = seats.get(1).get("seatId").asLong();

    mockMvc.perform(get("/api/seats/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

    MvcResult firstHold = mockMvc.perform(post("/api/reservations/hold")
            .header("Idempotency-Key", "hold-a1-user1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "showId": %d,
                  "seatId": %d,
                  "userId": 1
                }
                """.formatted(showId, seatA1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reused").value(false))
        .andExpect(jsonPath("$.reservation.status").value("HOLD"))
        .andReturn();

    long reservationA1 = read(firstHold).at("/reservation/reservationId").asLong();

    mockMvc.perform(post("/api/reservations/hold")
            .header("Idempotency-Key", "hold-a1-user1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "showId": %d,
                  "seatId": %d,
                  "userId": 1
                }
                """.formatted(showId, seatA1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reused").value(true))
        .andExpect(jsonPath("$.reservation.reservationId").value(reservationA1));

    mockMvc.perform(post("/api/reservations/{reservationId}/confirm", reservationA1)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESERVED"));

    MvcResult secondHold = mockMvc.perform(post("/api/reservations/hold")
            .header("Idempotency-Key", "hold-a2-user1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "showId": %d,
                  "seatId": %d,
                  "userId": 1
                }
                """.formatted(showId, seatA2)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reservation.status").value("HOLD"))
        .andReturn();

    long reservationA2 = read(secondHold).at("/reservation/reservationId").asLong();

    mockMvc.perform(post("/api/reservations/{reservationId}/cancel", reservationA2)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": 1
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc.perform(post("/api/reservations/hold")
            .header("Idempotency-Key", "hold-a1-user2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "showId": %d,
                  "seatId": %d,
                  "userId": 2
                }
                """.formatted(showId, seatA1)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));

    mockMvc.perform(get("/api/reservations").param("userId", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("CANCELLED"))
        .andExpect(jsonPath("$[1].status").value("RESERVED"));

    mockMvc.perform(get("/api/admin/statistics/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSeatCount").value(3))
        .andExpect(jsonPath("$.reservedCount").value(1))
        .andExpect(jsonPath("$.cancelledCount").value(1))
        .andExpect(jsonPath("$.requestCount").value(3))
        .andExpect(jsonPath("$.failedRequestCount").value(1))
        .andExpect(jsonPath("$.idempotentReuseCount").value(1))
        .andExpect(jsonPath("$.failureReasonCounts.SEAT_ALREADY_RESERVED").value(1));
  }

  @Test
  void hold가_만료되면_확정에_실패하고_좌석은_available로_돌아온다() throws Exception {
    long showId = createShow();
    JsonNode seats = createSeats(showId, "A1");
    long seatId = seats.get(0).get("seatId").asLong();

    MvcResult hold = mockMvc.perform(post("/api/reservations/hold")
            .header("Idempotency-Key", "hold-expire")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "showId": %d,
                  "seatId": %d,
                  "userId": 10
                }
                """.formatted(showId, seatId)))
        .andExpect(status().isCreated())
        .andReturn();

    long reservationId = read(hold).at("/reservation/reservationId").asLong();
    mutableClock.advanceSeconds(3);

    mockMvc.perform(post("/api/reservations/{reservationId}/confirm", reservationId)
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
        .andExpect(jsonPath("$[0].status").value("AVAILABLE"));

    mockMvc.perform(get("/api/reservations").param("userId", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("EXPIRED"));

    mockMvc.perform(get("/api/admin/statistics/shows/{showId}", showId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiredCount").value(1));
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
