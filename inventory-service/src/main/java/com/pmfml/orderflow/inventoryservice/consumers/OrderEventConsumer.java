package com.pmfml.orderflow.inventoryservice.consumers;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.inventoryservice.services.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ReservationService reservationService;
    private final ObjectMapper objectMapper;

    /**
     * Listens for new orders and attempts to reserve stock.
     */
    @KafkaListener(topics = EventTypes.ORDER_CREATED, groupId = "inventory-service")
    public void consumeOrderCreated(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.ORDER_CREATED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            reservationService.handleOrderCreated(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}", EventTypes.ORDER_CREATED, e.getMessage());
            // Rethrowing so the Kafka listener container can handle retries and DLQ routing
            throw new RuntimeException(e);
        }
    }

    /**
     * Listens for cancelled orders and releases held stock.
     */
    @KafkaListener(topics = EventTypes.ORDER_CANCELLED, groupId = "inventory-service")
    public void consumeOrderCancelled(@Payload String message) {
        log.debug("[Consumer] Received {} event", EventTypes.ORDER_CANCELLED);
        try {
            EventEnvelope event = objectMapper.readValue(message, EventEnvelope.class);
            reservationService.handleOrderCancelled(event);
        } catch (Exception e) {
            log.error("[Consumer] Error processing {} event: {}", EventTypes.ORDER_CANCELLED, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
