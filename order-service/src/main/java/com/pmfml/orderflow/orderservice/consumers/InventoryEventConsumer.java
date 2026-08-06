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
 * Kafka listener for failure events produced by the Inventory Service.
 *
 * <p>Listens to {@code inventory.reservation-failed} and delegates processing
 * to {@link SagaReactionService}. Exceptions are re-thrown so the Spring Kafka
 * error handler can apply retry + DLT routing.
 *
 * <p>Note: {@code inventory.reserved} is consumed by the Payment Service,
 * not the Order Service. The Order Service only reacts to the failure case.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final SagaReactionService sagaReactionService;
    private final ObjectMapper objectMapper;

    /**
     * Processes failed inventory reservations, triggering order cancellation.
     *
     * @param message raw JSON event envelope
     */
    @KafkaListener(topics = EventTypes.INVENTORY_RESERVATION_FAILED, groupId = "order-service")
    public void consumeInventoryReservationFailed(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.INVENTORY_RESERVATION_FAILED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            sagaReactionService.handleInventoryReservationFailed(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}",
                    EventTypes.INVENTORY_RESERVATION_FAILED, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
