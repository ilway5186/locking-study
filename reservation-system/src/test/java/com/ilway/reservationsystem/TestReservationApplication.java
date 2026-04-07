package com.ilway.reservationsystem;

import org.springframework.boot.SpringApplication;

public class TestReservationApplication {

  public static void main(String[] args) {
    SpringApplication.from(ReservationSystemApplication::main)
      .with(TestContainersConfiguration.class)
      .run(args);
  }

}
