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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SagaReactionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    /**
     * A real serializer — mocking it would let the test pass while asserting
     * nothing about the actual outbox payload structure.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private SagaReactionService sagaReactionService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @Captor
    private ArgumentCaptor<ProcessedEvent> processedEventCaptor;

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String TENANT_ID = "tenant-test";

    @BeforeEach
    void setUp() {
        sagaReactionService = new SagaReactionService(
                orderRepository,
                outboxEventRepository,
                processedEventRepository,
                objectMapper
        );
    }

    // ---- Helpers ----

    private Order buildPendingOrder() {
        return Order.builder()
                .tenantId(TENANT_ID)
                .status(OrderStatus.PENDING)
                .totalAmount(java.math.BigDecimal.valueOf(100))
                .build();
    }

    private EventEnvelope buildEvent(String eventType) {
        return buildEvent(eventType, Map.of("orderId", ORDER_ID.toString()));
    }

    private EventEnvelope buildEvent(String eventType, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID(), eventType, TENANT_ID, Instant.now(), payload);
    }

    /**
     * Sets the id field via reflection since Order.id is JPA-generated and has
     * no public setter.
     */
    private static void setIdViaReflection(Order order, UUID id) {
        try {
            java.lang.reflect.Field field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ================================================================
    // payment.authorized → CONFIRMED
    // ================================================================

    @Nested
    class PaymentAuthorized {

        @Test
        void shouldConfirmOrderAndWriteOutboxEvent() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_AUTHORIZED);
            Order order = buildPendingOrder();
            setIdViaReflection(order, ORDER_ID);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // When
            sagaReactionService.handlePaymentAuthorized(event);

            // Then — order transitioned to CONFIRMED
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            // Then — outbox event written for orders.confirmed
            verify(outboxEventRepository).save(outboxEventCaptor.capture());
            OutboxEvent outbox = outboxEventCaptor.getValue();
            assertThat(outbox.getEventType()).isEqualTo(EventTypes.ORDER_CONFIRMED);
            assertThat(outbox.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(outbox.getTenantId()).isEqualTo(TENANT_ID);

            // Then — idempotency record written
            verify(processedEventRepository).save(processedEventCaptor.capture());
            assertThat(processedEventCaptor.getValue().getEventId()).isEqualTo(event.eventId());
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldWriteOutboxPayloadWithOrderIdAndStatus() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_AUTHORIZED);
            Order order = buildPendingOrder();
            setIdViaReflection(order, ORDER_ID);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // When
            sagaReactionService.handlePaymentAuthorized(event);

            // Then — payload matches the documented contract
            verify(outboxEventRepository).save(outboxEventCaptor.capture());
            Map<String, Object> payload = objectMapper.readValue(
                    outboxEventCaptor.getValue().getPayload(), Map.class);

            assertThat(payload)
                    .containsOnlyKeys("orderId", "status")
                    .containsEntry("orderId", ORDER_ID.toString())
                    .containsEntry("status", "CONFIRMED");
        }
    }

    // ================================================================
    // payment.failed → CANCELLED
    // ================================================================

    @Nested
    class PaymentFailed {

        @Test
        void shouldCancelOrderAndWriteOutboxEvent() {
            // Given
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("orderId", ORDER_ID.toString());
            payload.put("reason", "card_declined");

            EventEnvelope event = buildEvent(EventTypes.PAYMENT_FAILED, payload);
            Order order = buildPendingOrder();
            setIdViaReflection(order, ORDER_ID);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // When
            sagaReactionService.handlePaymentFailed(event);

            // Then
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);

            verify(outboxEventRepository).save(outboxEventCaptor.capture());
            assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(EventTypes.ORDER_CANCELLED);
        }
    }

    // ================================================================
    // inventory.reservation-failed → CANCELLED
    // ================================================================

    @Nested
    class InventoryReservationFailed {

        @Test
        void shouldCancelOrderAndWriteOutboxEvent() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.INVENTORY_RESERVATION_FAILED);
            Order order = buildPendingOrder();
            setIdViaReflection(order, ORDER_ID);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

            // When
            sagaReactionService.handleInventoryReservationFailed(event);

            // Then
            verify(orderRepository).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);

            verify(outboxEventRepository).save(outboxEventCaptor.capture());
            assertThat(outboxEventCaptor.getValue().getEventType()).isEqualTo(EventTypes.ORDER_CANCELLED);

            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }
    }

    // ================================================================
    // Idempotency
    // ================================================================

    @Nested
    class Idempotency {

        @Test
        void shouldSkipAlreadyProcessedPaymentAuthorizedEvent() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_AUTHORIZED);
            given(processedEventRepository.existsById(event.eventId())).willReturn(true);

            // When
            sagaReactionService.handlePaymentAuthorized(event);

            // Then — no business effect
            verify(orderRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void shouldSkipAlreadyProcessedPaymentFailedEvent() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_FAILED);
            given(processedEventRepository.existsById(event.eventId())).willReturn(true);

            // When
            sagaReactionService.handlePaymentFailed(event);

            // Then
            verify(orderRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void shouldSkipAlreadyProcessedInventoryReservationFailedEvent() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.INVENTORY_RESERVATION_FAILED);
            given(processedEventRepository.existsById(event.eventId())).willReturn(true);

            // When
            sagaReactionService.handleInventoryReservationFailed(event);

            // Then
            verify(orderRepository, never()).findById(any());
            verify(orderRepository, never()).save(any());
        }
    }

    // ================================================================
    // Out-of-order / late events
    // ================================================================

    @Nested
    class OutOfOrderEvents {

        @Test
        void shouldSkipPaymentAuthorizedForAlreadyCancelledOrder() {
            // Given — order was already cancelled by an earlier inventory.reservation-failed
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_AUTHORIZED);
            Order cancelledOrder = Order.builder()
                    .tenantId(TENANT_ID)
                    .status(OrderStatus.CANCELLED)
                    .totalAmount(java.math.BigDecimal.valueOf(100))
                    .build();
            setIdViaReflection(cancelledOrder, ORDER_ID);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(cancelledOrder));

            // When
            sagaReactionService.handlePaymentAuthorized(event);

            // Then — no state change, no outbox event, but idempotency recorded
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }

        @Test
        void shouldSkipPaymentFailedForAlreadyConfirmedOrder() {
            // Given — order was already confirmed
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_FAILED);
            Order confirmedOrder = Order.builder()
                    .tenantId(TENANT_ID)
                    .status(OrderStatus.CONFIRMED)
                    .totalAmount(java.math.BigDecimal.valueOf(100))
                    .build();
            setIdViaReflection(confirmedOrder, ORDER_ID);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(confirmedOrder));

            // When
            sagaReactionService.handlePaymentFailed(event);

            // Then
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
            verify(processedEventRepository).save(any(ProcessedEvent.class));
        }
    }

    // ================================================================
    // Order not found
    // ================================================================

    @Nested
    class OrderNotFound {

        @Test
        void shouldHandlePaymentAuthorizedForNonExistentOrder() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_AUTHORIZED);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            // When — should not throw
            sagaReactionService.handlePaymentAuthorized(event);

            // Then — no state change, no outbox event
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void shouldHandlePaymentFailedForNonExistentOrder() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.PAYMENT_FAILED);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            // When — should not throw
            sagaReactionService.handlePaymentFailed(event);

            // Then
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        void shouldHandleInventoryReservationFailedForNonExistentOrder() {
            // Given
            EventEnvelope event = buildEvent(EventTypes.INVENTORY_RESERVATION_FAILED);

            given(processedEventRepository.existsById(event.eventId())).willReturn(false);
            given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

            // When — should not throw
            sagaReactionService.handleInventoryReservationFailed(event);

            // Then
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }
    }
}
