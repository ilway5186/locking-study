package com.ilway.reservation.support;

import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MutableClockTestConfiguration {

  @Bean
  @Primary
  MutableClock mutableClock() {
    return new MutableClock(Instant.parse("2026-03-31T00:00:00Z"), ZoneOffset.UTC);
  }
}
