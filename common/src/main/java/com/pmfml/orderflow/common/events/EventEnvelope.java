package com.pmfml.orderflow.common.events;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical wire format for every domain event exchanged over Kafka.
 *
 * <p>Defined in {@code docs/ARCHITECTURE.md} §7.2 and documented in
 * {@code docs/EVENTS.md}. It lives in the {@code common} module so that
 * producers and consumers share a single source of truth for the contract
 * instead of re-declaring it per service.
 *
 * <p><strong>Why {@code eventId} matters:</strong> the Outbox poller offers an
 * at-least-once guarantee, so the same event can legitimately reach Kafka more
 * than once. §7.5 requires every consumer to deduplicate against a
 * {@code processed_events} store keyed by {@code eventId}. For that to work the
 * identifier must be <em>stable across republications</em>, which is why
 * producers derive it from the persisted outbox row id rather than generating a
 * fresh UUID at publish time.
 *
 * @param eventId    stable unique identifier, used by consumers for deduplication
 * @param eventType  topic-qualified event name, e.g. {@code orders.created}
 * @param tenantId   owning tenant, propagated for multi-tenant isolation
 * @param occurredAt instant the event was recorded by the producer, not published
 * @param payload    event-specific data; see {@code docs/EVENTS.md} per event type
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String tenantId,
        Instant occurredAt,
        Map<String, Object> payload
) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        // LinkedHashMap rather than Map.copyOf: the latter does not preserve
        // insertion order, which would make the serialized field order vary
        // between runs for a contract that is documented field by field.
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }
}
