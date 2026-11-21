package com.asd.monopoly_bank;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real PostgreSQL database.
 *
 * <p>One container is started once per JVM (singleton pattern) and shared by every subclass,
 * so the Spring test context can be cached across test classes. Testcontainers' Ryuk
 * sidecar removes the container when the JVM exits.
 *
 * <p>{@link ServiceConnection} makes Spring Boot point the datasource at this container,
 * so no {@code spring.datasource.*} properties are needed in tests.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

	static {
		POSTGRES.start();
	}
}
