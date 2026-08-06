package com.pmfml.orderflow.orderservice.repositories;

import com.pmfml.orderflow.orderservice.entities.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link ProcessedEvent} idempotency records.
 *
 * <p>{@code existsById(eventId)} is the primary query, used by Saga reaction
 * handlers before processing an incoming Kafka event.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
