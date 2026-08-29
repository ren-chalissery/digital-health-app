package io.simplicity.training;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL and Redis for integration tests. H2 would leave the Flyway migrations
 * unexercised and rule out Postgres-specific SQL such as citext and jsonb.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    // Same major version as RDS in the deployed environment, so a migration that passes here
    // cannot fail there on a version difference.
    return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
  }

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redisContainer() {
    return new GenericContainer<>(DockerImageName.parse("valkey/valkey:8-alpine"))
        .withExposedPorts(6379);
  }
}
