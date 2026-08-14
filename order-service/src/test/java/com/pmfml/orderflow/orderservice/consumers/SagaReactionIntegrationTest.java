package com.pmfml.orderflow.orderservice.consumers;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.orderservice.TestcontainersConfiguration;
import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.entities.ProcessedEvent;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.repositories.OrderRepository;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import com.pmfml.orderflow.orderservice.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SagaReactionIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final String tenantId = "test-tenant";
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        outboxEventRepository.deleteAll();
        processedEventRepository.deleteAll();

        // Create a real PENDING order in the database for each test
        Order order = Order.builder()
                .tenantId(tenantId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("150.50"))
                .build();
        pendingOrder = orderRepository.save(order);
    }

    @Test
    void shouldConfirmOrderOnPaymentAuthorized() throws Exception {
        // Arrange
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.PAYMENT_AUTHORIZED,
                tenantId,
                Instant.now(),
                Map.of("orderId", pendingOrder.getId().toString())
        );

        // Act
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTypes.PAYMENT_AUTHORIZED, pendingOrder.getId().toString(), message).get();

        // Wait for async consumer processing
        Thread.sleep(5000);

        // Assert — Order status transitioned to CONFIRMED
        Order updatedOrder = orderRepository.findById(pendingOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Assert — Outbox event written
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo(EventTypes.ORDER_CONFIRMED);
        assertThat(outboxEvents.getFirst().getAggregateId()).isEqualTo(pendingOrder.getId());

        // Assert — Idempotency recorded
        List<ProcessedEvent> processed = processedEventRepository.findAll();
        assertThat(processed).hasSize(1);
        assertThat(processed.getFirst().getEventId()).isEqualTo(event.eventId());
    }

    @Test
    void shouldCancelOrderOnPaymentFailed() throws Exception {
        // Arrange
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.PAYMENT_FAILED,
                tenantId,
                Instant.now(),
                Map.of(
                        "orderId", pendingOrder.getId().toString(),
                        "reason", "Insufficient funds"
                )
        );

        // Act
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTypes.PAYMENT_FAILED, pendingOrder.getId().toString(), message).get();

        // Wait for async consumer processing
        Thread.sleep(5000);

        // Assert — Order status transitioned to CANCELLED
        Order updatedOrder = orderRepository.findById(pendingOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Assert — Outbox event written
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo(EventTypes.ORDER_CANCELLED);
    }

    @Test
    void shouldCancelOrderOnInventoryReservationFailed() throws Exception {
        // Arrange
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.INVENTORY_RESERVATION_FAILED,
                tenantId,
                Instant.now(),
                Map.of("orderId", pendingOrder.getId().toString())
        );

        // Act
        String message = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(EventTypes.INVENTORY_RESERVATION_FAILED, pendingOrder.getId().toString(), message).get();

        // Wait for async consumer processing
        Thread.sleep(5000);

        // Assert — Order status transitioned to CANCELLED
        Order updatedOrder = orderRepository.findById(pendingOrder.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Assert — Outbox event written
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);
        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo(EventTypes.ORDER_CANCELLED);
    }

    @Test
    void shouldSkipDuplicateEvent() throws Exception {
        // Arrange
        UUID eventId = UUID.randomUUID();
        EventEnvelope event = new EventEnvelope(
                eventId,
                EventTypes.PAYMENT_AUTHORIZED,
                tenantId,
                Instant.now(),
                Map.of("orderId", pendingOrder.getId().toString())
        );
        String message = objectMapper.writeValueAsString(event);

        // Act — send the same event twice
        kafkaTemplate.send(EventTypes.PAYMENT_AUTHORIZED, pendingOrder.getId().toString(), message).get();
        Thread.sleep(5000); // Give it time to process the first one
        
        // At this point, order should be confirmed and outbox should have 1 event.
        // If we send it again, we expect idempotency logic to skip it entirely.
        kafkaTemplate.send(EventTypes.PAYMENT_AUTHORIZED, pendingOrder.getId().toString(), message).get();
        Thread.sleep(5000);

        // Assert — only one outbox event created (idempotent)
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        // Assert — only one processed_events record
        List<ProcessedEvent> processed = processedEventRepository.findAll();
        assertThat(processed).hasSize(1);
    }
}
