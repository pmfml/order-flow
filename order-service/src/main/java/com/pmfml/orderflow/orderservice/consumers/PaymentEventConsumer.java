package com.pmfml.orderflow.orderservice.consumers;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.orderservice.services.SagaReactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka listener for events produced by the Payment Service.
 *
 * <p>Listens to {@code payment.authorized} and {@code payment.failed} topics
 * and delegates processing to {@link SagaReactionService}. Exceptions are
 * re-thrown so the Spring Kafka error handler can apply retry + DLT routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final SagaReactionService sagaReactionService;
    private final ObjectMapper objectMapper;

    /**
     * Processes successful payment authorizations.
     *
     * @param message raw JSON event envelope
     */
    @KafkaListener(topics = EventTypes.PAYMENT_AUTHORIZED, groupId = "order-service")
    public void consumePaymentAuthorized(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.PAYMENT_AUTHORIZED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            sagaReactionService.handlePaymentAuthorized(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}",
                    EventTypes.PAYMENT_AUTHORIZED, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Processes failed payment authorizations.
     *
     * @param message raw JSON event envelope
     */
    @KafkaListener(topics = EventTypes.PAYMENT_FAILED, groupId = "order-service")
    public void consumePaymentFailed(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.PAYMENT_FAILED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            sagaReactionService.handlePaymentFailed(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}",
                    EventTypes.PAYMENT_FAILED, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Processes successful payment captures.
     *
     * @param message raw JSON event envelope
     */
    @KafkaListener(topics = EventTypes.PAYMENT_CAPTURED, groupId = "order-service")
    public void consumePaymentCaptured(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.PAYMENT_CAPTURED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            sagaReactionService.handlePaymentCaptured(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}",
                    EventTypes.PAYMENT_CAPTURED, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
