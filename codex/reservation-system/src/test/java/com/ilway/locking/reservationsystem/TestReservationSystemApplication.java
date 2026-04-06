package com.ilway.locking.reservationsystem;

import org.springframework.boot.SpringApplication;

public class TestReservationSystemApplication {

  public static void main(String[] args) {
    SpringApplication.from(ReservationSystemApplication::main).with(TestcontainersConfiguration.class).run(args);
  }

}
