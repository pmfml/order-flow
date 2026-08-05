package com.pmfml.orderflow.paymentservice.consumers;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.paymentservice.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Kafka listener for events produced by the Inventory Service.
 *
 * <p>Listens to {@code inventory.reserved} and delegates payment authorization
 * to {@link PaymentService}. Exceptions are re-thrown so the Spring Kafka
 * error handler can apply retry + DLT routing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = EventTypes.INVENTORY_RESERVED, groupId = "payment-service")
    public void consumeInventoryReserved(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.INVENTORY_RESERVED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            paymentService.handleInventoryReserved(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}",
                    EventTypes.INVENTORY_RESERVED, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
