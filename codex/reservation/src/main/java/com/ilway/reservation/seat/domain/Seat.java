package com.ilway.reservation.seat.domain;

import com.ilway.reservation.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "seats",
    indexes = {
        @Index(name = "idx_seat_show_id", columnList = "showId")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_seat_show_id_seat_number", columnNames = {"showId", "seatNumber"})
    }
)
public class Seat extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long showId;

  @Column(nullable = false, length = 30)
  private String seatNumber;

  protected Seat() {
  }

  public Seat(Long showId, String seatNumber) {
    this.showId = showId;
    this.seatNumber = seatNumber;
  }

  public Long getId() {
    return id;
  }

  public Long getShowId() {
    return showId;
  }

  public String getSeatNumber() {
    return seatNumber;
  }
}
