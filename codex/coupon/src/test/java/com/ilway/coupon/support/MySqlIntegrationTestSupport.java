package com.ilway.coupon.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class MySqlIntegrationTestSupport {

  @Container
  static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.4")
      .withDatabaseName("coupon_test")
      .withUsername("test")
      .withPassword("test");

  @DynamicPropertySource
  static void registerMySqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }
}
