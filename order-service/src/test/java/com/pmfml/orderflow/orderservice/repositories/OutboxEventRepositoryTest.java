package com.pmfml.orderflow.orderservice.repositories;

import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldReturnOnlyUnprocessedEvents() {
        OutboxEvent unprocessed = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .payload("{\"orderId\": \"abc\"}")
                .build();

        OutboxEvent processed = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .payload("{\"orderId\": \"def\"}")
                .processedAt(Instant.now())
                .build();

        outboxEventRepository.saveAll(List.of(unprocessed, processed));

        List<OutboxEvent> results = outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getAggregateId()).isEqualTo(unprocessed.getAggregateId());
        assertThat(results.get(0).getProcessedAt()).isNull();
    }

    @Test
    void shouldReturnEmptyWhenAllEventsAreProcessed() {
        OutboxEvent processed = OutboxEvent.builder()
                .aggregateType("Order")
                .aggregateId(UUID.randomUUID())
                .eventType("OrderCreated")
                .payload("{}")
                .processedAt(Instant.now())
                .build();

        outboxEventRepository.save(processed);

        List<OutboxEvent> results = outboxEventRepository.findByProcessedAtIsNullOrderByCreatedAtAsc();

        assertThat(results).isEmpty();
    }
}
