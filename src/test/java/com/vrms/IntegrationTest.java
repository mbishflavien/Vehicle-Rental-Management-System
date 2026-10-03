package com.vrms;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests: the real PostgreSQL and MongoDB run in Docker (Testcontainers),
 * started once and shared by every test class. Spring Boot wires the connections automatically.
 */
@Testcontainers
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static {
        POSTGRES.start();
        MONGO.start();
    }
}
