package com.ilway.reservationsystem.seat.domain;

import com.ilway.reservationsystem.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "seats",
  indexes = {@Index(name = "idx_seat_show_id", columnList = "show_id")},
  uniqueConstraints = {
    @UniqueConstraint(name = "uk_seat_show_id_seat_number", columnNames = {"show_id", "seat_number"})
  }
)
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Seat extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "show_id", nullable = false)
  private Long showId;

  @Column(name = "seat_number", nullable = false, length = 30)
  private String seatNumber;

  public Seat(Long showId, String seatNumber) {
    this.showId = showId;
    this.seatNumber = seatNumber;
  }

}
