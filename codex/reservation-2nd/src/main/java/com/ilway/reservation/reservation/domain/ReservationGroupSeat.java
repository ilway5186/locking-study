package com.ilway.reservation.reservation.domain;

import com.ilway.reservation.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "reservation_group_seats",
    indexes = {
        @Index(name = "idx_group_seat_reservation_group_id", columnList = "reservationGroupId"),
        @Index(name = "idx_group_seat_seat_id", columnList = "seatId")
    }
)
public class ReservationGroupSeat extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservationGroupId", nullable = false)
  private ReservationGroup reservationGroup;

  @Column(nullable = false)
  private Long seatId;

  @Column(nullable = false, length = 30)
  private String seatNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReservationGroupSeatStatus status;

  protected ReservationGroupSeat() {
  }

  private ReservationGroupSeat(
      ReservationGroup reservationGroup,
      Long seatId,
      String seatNumber,
      ReservationGroupSeatStatus status
  ) {
    this.reservationGroup = reservationGroup;
    this.seatId = seatId;
    this.seatNumber = seatNumber;
    this.status = status;
  }

  public static ReservationGroupSeat hold(ReservationGroup reservationGroup, Long seatId, String seatNumber) {
    return new ReservationGroupSeat(reservationGroup, seatId, seatNumber, ReservationGroupSeatStatus.HOLD);
  }

  public void moveToPaymentPending() {
    status = ReservationGroupSeatStatus.PAYMENT_PENDING;
  }

  public void reserve() {
    status = ReservationGroupSeatStatus.RESERVED;
  }

  public void cancel() {
    status = ReservationGroupSeatStatus.CANCELLED;
  }

  public void expire() {
    status = ReservationGroupSeatStatus.EXPIRED;
  }

  public Long getId() {
    return id;
  }

  public ReservationGroup getReservationGroup() {
    return reservationGroup;
  }

  public Long getSeatId() {
    return seatId;
  }

  public String getSeatNumber() {
    return seatNumber;
  }

  public ReservationGroupSeatStatus getStatus() {
    return status;
  }
}
