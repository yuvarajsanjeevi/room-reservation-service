package com.example.reservation;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A throwaway Postgres for the test that needs the real database. H2 stands in everywhere else, but
 * it would not exercise the Flyway migration, which is Postgres-specific SQL.
 *
 * {@link #dockerAvailable()} lets that test skip rather than fail without Docker.
 */
@TestConfiguration
public class PostgresTestContainer {

    static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
