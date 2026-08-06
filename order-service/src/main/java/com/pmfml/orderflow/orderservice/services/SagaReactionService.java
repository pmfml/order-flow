package com.pmfml.orderflow.orderservice.services;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.entities.ProcessedEvent;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.repositories.OrderRepository;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import com.pmfml.orderflow.orderservice.repositories.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reacts to Saga events that finalize an order's lifecycle.
 *
 * <p>Listens (indirectly, via the Kafka consumer layer) for:
 * <ul>
 *   <li>{@code payment.authorized} → transitions the order to {@link OrderStatus#CONFIRMED}
 *       and writes an {@code orders.confirmed} outbox event.</li>
 *   <li>{@code payment.failed} → transitions the order to {@link OrderStatus#CANCELLED}
 *       and writes an {@code orders.cancelled} outbox event.</li>
 *   <li>{@code inventory.reservation-failed} → transitions the order to
 *       {@link OrderStatus#CANCELLED} and writes an {@code orders.cancelled} outbox event.</li>
 * </ul>
 *
 * <p>Every handler runs inside a single {@code @Transactional} boundary that
 * atomically commits the status change, the outbox row, and the idempotency
 * record. This preserves the Transactional Outbox guarantee established in
 * Phase 1 — the existing {@link com.pmfml.orderflow.orderservice.outbox.OutboxPublisher}
 * picks up the new rows automatically.
 *
 * <p><strong>Out-of-order events:</strong> if a late event arrives for an order
 * that already left {@code PENDING} (e.g. {@code payment.authorized} after
 * the order was cancelled by an earlier {@code inventory.reservation-failed}),
 * the handler logs a warning and skips the business effect. The event is still
 * recorded as processed so it is not retried.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaReactionService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handles a successful payment authorization.
     *
     * <p>Transitions the order to {@link OrderStatus#CONFIRMED} and writes
     * an {@code orders.confirmed} event to the outbox.
     *
     * @param event the {@code payment.authorized} event envelope
     */
    @Transactional
    public void handlePaymentAuthorized(EventEnvelope event) {
        if (alreadyProcessed(event)) {
            return;
        }

        String orderId = extractOrderId(event);
        log.info("[SagaReaction] Processing {} for order {}", EventTypes.PAYMENT_AUTHORIZED, orderId);

        Optional<Order> orderOpt = findOrder(orderId);
        if (orderOpt.isEmpty()) {
            return;
        }

        Order order = orderOpt.get();
        if (!isPending(order, EventTypes.PAYMENT_AUTHORIZED)) {
            recordProcessed(event);
            return;
        }

        order.confirm();
        orderRepository.save(order);
        writeOutboxEvent(order, EventTypes.ORDER_CONFIRMED);
        recordProcessed(event);

        log.info("[SagaReaction] Order confirmed: orderId={}, tenantId={}",
                orderId, order.getTenantId());
    }

    /**
     * Handles a failed payment authorization.
     *
     * <p>Transitions the order to {@link OrderStatus#CANCELLED} and writes
     * an {@code orders.cancelled} event to the outbox so the Inventory Service
     * can release its stock reservation.
     *
     * @param event the {@code payment.failed} event envelope
     */
    @Transactional
    public void handlePaymentFailed(EventEnvelope event) {
        if (alreadyProcessed(event)) {
            return;
        }

        String orderId = extractOrderId(event);
        String reason = extractReason(event);
        log.info("[SagaReaction] Processing {} for order {}: reason={}",
                EventTypes.PAYMENT_FAILED, orderId, reason);

        cancelOrder(event, orderId, "payment failed: " + reason);
    }

    /**
     * Handles a failed inventory reservation.
     *
     * <p>Transitions the order to {@link OrderStatus#CANCELLED} and writes
     * an {@code orders.cancelled} event to the outbox. No stock was reserved,
     * so the cancellation event is purely informational for downstream
     * consumers and audit.
     *
     * @param event the {@code inventory.reservation-failed} event envelope
     */
    @Transactional
    public void handleInventoryReservationFailed(EventEnvelope event) {
        if (alreadyProcessed(event)) {
            return;
        }

        String orderId = extractOrderId(event);
        log.info("[SagaReaction] Processing {} for order {}",
                EventTypes.INVENTORY_RESERVATION_FAILED, orderId);

        cancelOrder(event, orderId, "inventory reservation failed");
    }

    // ---- Internal helpers ----

    /**
     * Shared cancellation logic for both {@code payment.failed} and
     * {@code inventory.reservation-failed}.
     */
    private void cancelOrder(EventEnvelope event, String orderId, String reason) {
        Optional<Order> orderOpt = findOrder(orderId);
        if (orderOpt.isEmpty()) {
            recordProcessed(event);
            return;
        }

        Order order = orderOpt.get();
        if (!isPending(order, event.eventType())) {
            recordProcessed(event);
            return;
        }

        order.cancel();
        orderRepository.save(order);
        writeOutboxEvent(order, EventTypes.ORDER_CANCELLED);
        recordProcessed(event);

        log.info("[SagaReaction] Order cancelled: orderId={}, tenantId={}, reason={}",
                orderId, order.getTenantId(), reason);
    }

    /**
     * Checks whether the event has already been processed (idempotency guard).
     *
     * @return {@code true} if the event should be skipped
     */
    private boolean alreadyProcessed(EventEnvelope event) {
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("[SagaReaction] Event {} already processed, skipping.", event.eventId());
            return true;
        }
        return false;
    }

    /**
     * Records the event as processed in the same transaction as the business effect.
     */
    private void recordProcessed(EventEnvelope event) {
        ProcessedEvent record = ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .build();

        try {
            processedEventRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate — safe to ignore, the first writer wins
            log.info("[SagaReaction] Concurrent duplicate for event {}, skipping.", event.eventId());
        }
    }

    /**
     * Looks up the order by its UUID. Logs a warning and returns empty if not found.
     */
    private Optional<Order> findOrder(String orderId) {
        try {
            UUID id = UUID.fromString(orderId);
            Optional<Order> order = orderRepository.findById(id);
            if (order.isEmpty()) {
                log.warn("[SagaReaction] Order not found: orderId={}", orderId);
            }
            return order;
        } catch (IllegalArgumentException e) {
            log.error("[SagaReaction] Invalid orderId format: {}", orderId);
            return Optional.empty();
        }
    }

    /**
     * Checks whether the order is still in {@link OrderStatus#PENDING}.
     * If not, logs a warning about the late/out-of-order event and returns {@code false}.
     */
    private boolean isPending(Order order, String incomingEventType) {
        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[SagaReaction] Ignoring late {} event for order {}: status is already {}",
                    incomingEventType, order.getId(), order.getStatus());
            return false;
        }
        return true;
    }

    private String extractOrderId(EventEnvelope event) {
        return (String) event.payload().get("orderId");
    }

    private String extractReason(EventEnvelope event) {
        Object reason = event.payload().get("reason");
        return reason != null ? reason.toString() : "unknown";
    }

    /**
     * Writes a Saga outcome event to the outbox table.
     *
     * <p>The payload follows the same structure as {@code orders.created} (minus
     * the line items, which are not needed by downstream consumers of confirmed/
     * cancelled events). The {@link com.pmfml.orderflow.orderservice.outbox.OutboxPublisher}
     * wraps this row in the canonical {@link EventEnvelope} at publish time.
     */
    private void writeOutboxEvent(Order order, String eventType) {
        try {
            Map<String, Object> payloadData = new LinkedHashMap<>();
            payloadData.put("orderId", order.getId().toString());
            payloadData.put("status", order.getStatus().name());

            String payload = objectMapper.writeValueAsString(payloadData);

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(order.getId())
                    .tenantId(order.getTenantId())
                    .eventType(eventType)
                    .payload(payload)
                    .build();

            outboxEventRepository.save(event);
            log.debug("[SagaReaction] Wrote {} outbox event: orderId={}", eventType, order.getId());

        } catch (JacksonException e) {
            log.error("[SagaReaction] Failed to serialize outbox payload for order {}", order.getId(), e);
            throw new RuntimeException(
                    "Failed to serialize outbox event payload for order %s".formatted(order.getId()), e);
        }
    }
}
