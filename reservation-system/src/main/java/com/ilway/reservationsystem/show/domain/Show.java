package com.ilway.reservationsystem.show.domain;

import com.ilway.reservationsystem.common.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "shows")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor()
public class Show extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  private Instant startAt;

  private Instant endAt;

  private Instant bookingOpenAt;

  private Instant bookingCloseAt;

  public Show(String name, Instant startAt, Instant endAt, Instant bookingOpenAt, Instant bookingCloseAt) {
    this.name = name;
    this.startAt = startAt;
    this.endAt = endAt;
    this.bookingOpenAt = bookingOpenAt;
    this.bookingCloseAt = bookingCloseAt;
  }


}
