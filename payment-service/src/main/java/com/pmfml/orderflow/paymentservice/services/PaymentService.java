package com.pmfml.orderflow.paymentservice.services;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.paymentservice.entities.PaymentStatus;
import com.pmfml.orderflow.paymentservice.entities.PaymentTransaction;
import com.pmfml.orderflow.paymentservice.entities.ProcessedEvent;
import com.pmfml.orderflow.paymentservice.gateway.PaymentGateway;
import com.pmfml.orderflow.paymentservice.gateway.PaymentResult;
import com.pmfml.orderflow.paymentservice.repositories.PaymentTransactionRepository;
import com.pmfml.orderflow.paymentservice.repositories.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates payment authorization in response to {@code inventory.reserved} events.
 *
 * <p>The flow is:
 * <ol>
 *   <li>Idempotency check against {@code processed_events}.</li>
 *   <li>Call the {@link PaymentGateway} to authorize the amount.</li>
 *   <li>Persist a {@link PaymentTransaction} recording the outcome.</li>
 *   <li>Publish {@code payment.authorized} or {@code payment.failed}.</li>
 * </ol>
 *
 * <p>Steps 1–3 run inside a single {@code @Transactional} boundary so that the
 * idempotency record and the payment row are committed atomically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleInventoryReserved(EventEnvelope event) {
        // 1. Idempotency: skip if already processed
        if (processedEventRepository.existsById(event.eventId())) {
            log.info("[Payment] Event {} already processed, skipping.", event.eventId());
            return;
        }

        String orderId = (String) event.payload().get("orderId");
        String tenantId = event.tenantId();

        log.info("[Payment] Processing inventory.reserved for order {}", orderId);

        // 2. Call the payment gateway
        // The totalAmount comes from the original orders.created event and is
        // forwarded by the inventory service inside the inventory.reserved payload.
        // If it is absent, we fall back to zero (the gateway will reject it).
        BigDecimal amount = extractAmount(event);

        PaymentResult result = paymentGateway.authorize(orderId, tenantId, amount);

        // 3. Persist payment transaction
        PaymentTransaction tx = PaymentTransaction.builder()
                .tenantId(tenantId)
                .orderId(UUID.fromString(orderId))
                .amount(amount)
                .status(result.success() ? PaymentStatus.AUTHORIZED : PaymentStatus.FAILED)
                .stripePaymentIntentId(result.providerTransactionId())
                .build();
        paymentTransactionRepository.save(tx);

        // 4. Record idempotency marker (same transaction)
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.eventId())
                .eventType(event.eventType())
                .processedAt(Instant.now())
                .build();

        try {
            processedEventRepository.save(processedEvent);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate — safe to ignore, the first writer wins
            log.info("[Payment] Concurrent duplicate for event {}, skipping.", event.eventId());
            return;
        }

        // 5. Publish outcome event
        String outcomeType = result.success()
                ? EventTypes.PAYMENT_AUTHORIZED
                : EventTypes.PAYMENT_FAILED;

        publishOutcome(orderId, tenantId, outcomeType, result);
    }

    @Transactional
    public void handleWebhook(com.pmfml.orderflow.paymentservice.controllers.WebhookPayload payload) {
        log.info("[Payment] Processing webhook for order {}: status {}", payload.getOrderId(), payload.getStatus());

        // Find the payment transaction
        UUID orderId = UUID.fromString(payload.getOrderId());
        PaymentTransaction tx = paymentTransactionRepository.findByOrderIdAndTenantId(orderId, payload.getTenantId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for order " + payload.getOrderId()));

        // Update status if it's CAPTURED
        if ("CAPTURED".equals(payload.getStatus())) {
            tx.setStatus(PaymentStatus.CAPTURED);
        } else if ("FAILED".equals(payload.getStatus())) {
            tx.setStatus(PaymentStatus.FAILED);
        }
        
        paymentTransactionRepository.save(tx);

        // Publish event (no idempotency check here since webhook provider usually retries and we can just re-publish or check status)
        String outcomeType = "CAPTURED".equals(payload.getStatus()) ? EventTypes.PAYMENT_CAPTURED : EventTypes.PAYMENT_FAILED;
        
        // Use a dummy PaymentResult for the publish method signature
        PaymentResult result = new PaymentResult("CAPTURED".equals(payload.getStatus()), tx.getStripePaymentIntentId(), null);
        publishOutcome(payload.getOrderId(), tx.getTenantId(), outcomeType, result);
    }

    private BigDecimal extractAmount(EventEnvelope event) {
        Object raw = event.payload().get("totalAmount");
        if (raw instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        log.warn("[Payment] No totalAmount in event payload, defaulting to 0");
        return BigDecimal.ZERO;
    }

    private void publishOutcome(String orderId, String tenantId,
                                String outcomeType, PaymentResult result) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("orderId", orderId);
        if (result.providerTransactionId() != null) {
            payload.put("paymentIntentId", result.providerTransactionId());
        }
        if (result.failureReason() != null) {
            payload.put("reason", result.failureReason());
        }

        EventEnvelope outcomeEvent = new EventEnvelope(
                UUID.randomUUID(),
                outcomeType,
                tenantId,
                Instant.now(),
                payload
        );

        try {
            String message = objectMapper.writeValueAsString(outcomeEvent);
            kafkaTemplate.send(outcomeType, orderId, message)
                    .whenComplete((res, ex) -> {
                        if (ex != null) {
                            log.error("[Payment] Failed to publish {}", outcomeType, ex);
                        } else {
                            log.debug("[Payment] Published {}", outcomeType);
                        }
                    });
        } catch (Exception e) {
            log.error("[Payment] Failed to serialize outcome event {}", outcomeType, e);
            throw new RuntimeException(e);
        }
    }
}
