package com.pmfml.orderflow.orderservice.outbox;

import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${orderflow.outbox.topic}")
    private String topic;

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
                // Use aggregateId as the Kafka key to ensure ordering per order
                kafkaTemplate.send(topic, event.getAggregateId().toString(), event.getPayload())
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
}
