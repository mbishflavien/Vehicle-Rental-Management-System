package com.vrms;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests: real PostgreSQL, MongoDB, RabbitMQ and an SMTP inbox (Mailpit) run
 * in Docker through Testcontainers, started once and shared by every test class.
 */
@Testcontainers
@ActiveProfiles("test")
@Import(IntegrationTest.CleanDatabase.class)
public abstract class IntegrationTest {

    /** Every new test context starts from an empty database, built by the real Flyway migrations. */
    @TestConfiguration(proxyBeanMethods = false)
    static class CleanDatabase {
        @Bean
        FlywayMigrationStrategy cleanThenMigrate() {
            return flyway -> {
                flyway.clean();
                flyway.migrate();
            };
        }
    }

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    /** Catches outgoing email; its HTTP API lets tests read the inbox. */
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:latest").withExposedPorts(1025, 8025);

    static {
        POSTGRES.start();
        MONGO.start();
        RABBIT.start();
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void mail(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    static String mailpitApi() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1";
    }
}
