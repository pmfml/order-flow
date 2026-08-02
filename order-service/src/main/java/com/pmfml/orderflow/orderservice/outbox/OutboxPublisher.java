package com.pmfml.orderflow.orderservice.outbox;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls the outbox table for unprocessed events and publishes them to Kafka.
 *
 * <p>This is the second half of the <strong>Transactional Outbox</strong> pattern.
 * The first half (writing the event in the same transaction as the domain change)
 * happens in {@link com.pmfml.orderflow.orderservice.services.OrderService}.
 *
 * <p><strong>At-least-once guarantee:</strong> if the application crashes after
 * publishing to Kafka but before marking the event as processed, the event will
 * be re-published on the next poll cycle. Downstream consumers must be
 * <strong>idempotent</strong> (e.g., via a {@code processed_events} table).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() {
            };

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Scheduled poller that runs at a fixed interval.
     *
     * <p>Each event is published individually so that a failure on one event
     * does not block the rest. Events that fail to publish will remain with
     * {@code processedAt = null} and will be retried on the next cycle.
     */
    @Scheduled(fixedDelayString = "${orderflow.outbox.poll-interval-ms}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByProcessedAtIsNullOrderByCreatedAtAsc();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("[Outbox] Found {} pending event(s) to publish", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                String message = objectMapper.writeValueAsString(toEnvelope(event));

                // The event type doubles as the topic name (§7.1), so no mapping is
                // needed. Use aggregateId as the Kafka key to ensure ordering per order.
                kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), message)
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                log.error("[Outbox] Failed to publish event {} to Kafka",
                                        event.getId(), ex);
                            }
                        });

                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);

                log.debug("[Outbox] Published event: id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());

            } catch (Exception e) {
                log.error("[Outbox] Error processing event {}: {}",
                        event.getId(), e.getMessage(), e);
                // Do NOT mark as processed — it will be retried on the next cycle
            }
        }
    }

    /**
     * Wraps a stored outbox row in the canonical event envelope (§7.2).
     *
     * <p>Every envelope field is derived from the row itself, so republishing the
     * same row always yields a byte-identical message. In particular
     * {@code eventId} is the row's primary key, which is what allows consumers to
     * deduplicate retries of the at-least-once delivery.
     *
     * <p>The stored payload is parsed back from its JSON string so it nests as a
     * JSON object under {@code payload} instead of an escaped string literal. The
     * cost is one parse per published event, which is negligible next to the
     * network round trip.
     */
    private EventEnvelope toEnvelope(OutboxEvent event) {
        return new EventEnvelope(
                event.getId(),
                event.getEventType(),
                event.getTenantId(),
                event.getCreatedAt(),
                objectMapper.readValue(event.getPayload(), PAYLOAD_TYPE)
        );
    }
}
