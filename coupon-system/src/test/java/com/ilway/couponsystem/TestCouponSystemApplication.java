package com.ilway.couponsystem;

import org.springframework.boot.SpringApplication;

public class TestCouponSystemApplication {

  public static void main(String[] args) {
    SpringApplication.from(CouponSystemApplication::main).with(TestcontainersConfiguration.class).run(args);
  }

}
