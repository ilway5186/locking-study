package com.ilway.reservationsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestContainersConfiguration.class)
@SpringBootTest
public class ReservationApplicationTests {

  @Test
  void contextLoads() {

  }

}
