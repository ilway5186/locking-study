package com.ilway.reservation.show.domain;

import com.ilway.reservation.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "shows")
public class Show extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false)
  private Instant startAt;

  @Column(nullable = false)
  private Instant endAt;

  @Column(nullable = false)
  private Instant bookingOpenAt;

  @Column(nullable = false)
  private Instant bookingCloseAt;

  protected Show() {
  }

  public Show(String name, Instant startAt, Instant endAt, Instant bookingOpenAt, Instant bookingCloseAt) {
    this.name = name;
    this.startAt = startAt;
    this.endAt = endAt;
    this.bookingOpenAt = bookingOpenAt;
    this.bookingCloseAt = bookingCloseAt;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public Instant getBookingOpenAt() {
    return bookingOpenAt;
  }

  public Instant getBookingCloseAt() {
    return bookingCloseAt;
  }
}
