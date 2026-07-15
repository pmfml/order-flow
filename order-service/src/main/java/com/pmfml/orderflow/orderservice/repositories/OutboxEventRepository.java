package com.pmfml.orderflow.orderservice.repositories;

import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link OutboxEvent} persistence.
 *
 * <p>The primary consumer is the outbox poller (Phase 3), which reads
 * unprocessed events and publishes them to Kafka.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Returns all events that have not yet been published to Kafka,
     * ordered by creation time (FIFO).
     *
     * <p>This query benefits from the partial index
     * {@code idx_outbox_unprocessed} defined in V1.
     *
     * @return unprocessed outbox events in chronological order
     */
    List<OutboxEvent> findByProcessedAtIsNullOrderByCreatedAtAsc();
}
