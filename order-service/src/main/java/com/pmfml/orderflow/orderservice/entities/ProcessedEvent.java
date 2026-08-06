package com.pmfml.orderflow.orderservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks processed Kafka events to ensure idempotent consumption.
 *
 * <p>The primary key is the {@code eventId} from the canonical
 * {@link com.pmfml.orderflow.common.events.EventEnvelope}. A row is inserted
 * in the same transaction as the business effect so that both are committed
 * atomically — see {@code docs/EVENTS.md} §3 for the full contract.
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "event_type", updatable = false, nullable = false)
    private String eventType;

    @Column(name = "processed_at", updatable = false, nullable = false)
    private Instant processedAt;
}
