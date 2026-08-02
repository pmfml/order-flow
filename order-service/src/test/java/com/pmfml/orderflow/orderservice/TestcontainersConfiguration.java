package com.pmfml.orderflow.orderservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared containers for tests that need the full application context.
 *
 * <p>Image tags are pinned rather than floating on {@code latest}: builds stay
 * reproducible, and the PostgreSQL version matches the one in
 * {@code infra/docker-compose.yml}. That alignment matters for
 * {@link FlywayMigrationTest}, which validates the production schema and would
 * lose its point if it ran against a different major version.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.0"));
	}

	// Note: org.testcontainers.postgresql.PostgreSQLContainer is not generic,
	// unlike the legacy org.testcontainers.containers variant used by the
	// @DataJpaTest slices.
	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
