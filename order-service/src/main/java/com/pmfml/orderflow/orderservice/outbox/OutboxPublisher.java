package com.pmfml.orderflow.orderservice.outbox;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Polls the outbox table for unprocessed events and publishes them to Kafka.
 *
 * <p>This is the second half of the <strong>Transactional Outbox</strong> pattern.
 * The first half (writing the event in the same transaction as the domain change)
 * happens in {@link com.pmfml.orderflow.orderservice.services.OrderService}.
 *
 * <p><strong>At-least-once, never at-most-once:</strong> a row is marked processed
 * only after the broker has acknowledged the record. The publisher therefore
 * waits for each send to complete instead of firing and forgetting. If the
 * application crashes between the acknowledgement and the commit, the event is
 * re-published on the next cycle, so consumers must be <strong>idempotent</strong>
 * by deduplicating on {@code eventId} (see {@code docs/EVENTS.md} §3).
 *
 * <p>The cost of waiting is bounded by {@code orderflow.outbox.send-timeout-ms};
 * on timeout the row stays pending and is retried rather than silently dropped.
 *
 * <p><strong>Known limitation:</strong> a failed event does not hold back later
 * events for the same aggregate, so if one event for an order fails while a
 * subsequent one succeeds, consumers can observe them out of order. This cannot
 * happen today because Order Service emits a single event type, but it becomes
 * reachable in Phase 6 when {@code orders.confirmed} and {@code orders.cancelled}
 * are added.
 */
@Slf4j
@Component
public class OutboxPublisher {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE =
            new TypeReference<>() {
            };

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMs;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           @Value("${orderflow.outbox.send-timeout-ms}") long sendTimeoutMs) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeoutMs = sendTimeoutMs;
    }

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
                //
                // Block until the broker acknowledges. send() only queues the record,
                // so marking the row processed without waiting would discard events
                // whose write failed after this call returned.
                kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), message)
                        .get(sendTimeoutMs, TimeUnit.MILLISECONDS);

                event.setProcessedAt(Instant.now());
                outboxEventRepository.save(event);

                log.debug("[Outbox] Published event: id={}, type={}, aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());

            } catch (InterruptedException e) {
                // Restore the flag and abandon the cycle; remaining events stay
                // pending and are picked up after restart.
                Thread.currentThread().interrupt();
                log.warn("[Outbox] Interrupted while publishing event {}; aborting this cycle",
                        event.getId());
                return;

            } catch (Exception e) {
                log.error("[Outbox] Failed to publish event {} (type={}); will retry next cycle",
                        event.getId(), event.getEventType(), e);
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
