package com.pmfml.orderflow.inventoryservice.services;

import com.pmfml.orderflow.common.events.EventEnvelope;
import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.inventoryservice.entities.Product;
import com.pmfml.orderflow.inventoryservice.entities.ReservationStatus;
import com.pmfml.orderflow.inventoryservice.entities.StockReservation;
import com.pmfml.orderflow.inventoryservice.repositories.ProcessedEventRepository;
import com.pmfml.orderflow.inventoryservice.repositories.ProductRepository;
import com.pmfml.orderflow.inventoryservice.repositories.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReservationService reservationService;

    @Captor
    private ArgumentCaptor<List<Product>> productListCaptor;

    @Captor
    private ArgumentCaptor<List<StockReservation>> reservationListCaptor;

    private static final String TENANT_ID = "tenant-unit";
    private static final String ORDER_ID = UUID.randomUUID().toString();
    private static final String PRODUCT_ID = "prod-unit-1";

    // ---------------------------------------------------------------
    // handleOrderCreated
    // ---------------------------------------------------------------

    @Test
    void shouldReserveStockSuccessfully() throws Exception {
        EventEnvelope event = orderCreatedEvent(PRODUCT_ID, 3, 4501.5);
        Product product = productWithStock(PRODUCT_ID, 10);

        given(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .willReturn(Optional.of(product));
        given(objectMapper.writeValueAsString(any()))
                .willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        reservationService.handleOrderCreated(event);

        // Stock should be deducted
        verify(productRepository).saveAll(productListCaptor.capture());
        assertThat(productListCaptor.getValue().getFirst().getStockQuantity()).isEqualTo(7);

        // Reservation should be created with RESERVED status
        verify(stockReservationRepository).saveAll(reservationListCaptor.capture());
        StockReservation saved = reservationListCaptor.getValue().getFirst();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getQuantity()).isEqualTo(3);

        // Should publish inventory.reserved
        verify(kafkaTemplate).send(eq(EventTypes.INVENTORY_RESERVED), eq(ORDER_ID), anyString());
    }

    @Test
    void shouldSkipDuplicateOrderCreatedEvent() {
        EventEnvelope event = orderCreatedEvent(PRODUCT_ID, 3, 100.0);

        doThrow(new DuplicateKeyException("duplicate"))
                .when(processedEventRepository).insert(any(com.pmfml.orderflow.inventoryservice.entities.ProcessedEvent.class));

        reservationService.handleOrderCreated(event);

        // No reservation logic should execute
        verify(productRepository, never()).findByIdAndTenantId(anyString(), anyString());
        verify(stockReservationRepository, never()).saveAll(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldFailWhenProductNotFound() throws Exception {
        EventEnvelope event = orderCreatedEvent("prod-nonexistent", 1, 50.0);

        given(productRepository.findByIdAndTenantId("prod-nonexistent", TENANT_ID))
                .willReturn(Optional.empty());
        given(objectMapper.writeValueAsString(any()))
                .willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        reservationService.handleOrderCreated(event);

        // No products or reservations should be saved
        verify(productRepository, never()).saveAll(any());
        verify(stockReservationRepository, never()).saveAll(any());

        // Should publish inventory.reservation-failed
        verify(kafkaTemplate).send(eq(EventTypes.INVENTORY_RESERVATION_FAILED), eq(ORDER_ID), anyString());
    }

    @Test
    void shouldFailWhenInsufficientStock() throws Exception {
        EventEnvelope event = orderCreatedEvent(PRODUCT_ID, 100, 5000.0);
        Product product = productWithStock(PRODUCT_ID, 5);

        given(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .willReturn(Optional.of(product));
        given(objectMapper.writeValueAsString(any()))
                .willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        reservationService.handleOrderCreated(event);

        verify(productRepository, never()).saveAll(any());
        verify(kafkaTemplate).send(eq(EventTypes.INVENTORY_RESERVATION_FAILED), eq(ORDER_ID), anyString());
    }

    @Test
    void shouldForwardTotalAmountInReservedEvent() throws Exception {
        EventEnvelope event = orderCreatedEvent(PRODUCT_ID, 1, 1500.5);
        Product product = productWithStock(PRODUCT_ID, 10);

        ArgumentCaptor<Object> serializationCaptor = ArgumentCaptor.forClass(Object.class);

        given(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .willReturn(Optional.of(product));
        given(objectMapper.writeValueAsString(serializationCaptor.capture()))
                .willReturn("{}");
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        reservationService.handleOrderCreated(event);

        // Verify the serialized EventEnvelope contains the totalAmount
        EventEnvelope publishedEvent = (EventEnvelope) serializationCaptor.getValue();
        assertThat(publishedEvent.payload().get("totalAmount")).isEqualTo(1500.5);
        assertThat(publishedEvent.eventType()).isEqualTo(EventTypes.INVENTORY_RESERVED);
    }

    // ---------------------------------------------------------------
    // handleOrderCancelled
    // ---------------------------------------------------------------

    @Test
    void shouldReleaseStockOnCancellation() {
        EventEnvelope event = orderCancelledEvent();
        Product product = productWithStock(PRODUCT_ID, 7);
        StockReservation reservation = reservedReservation(PRODUCT_ID, 3);

        given(stockReservationRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(List.of(reservation));
        given(productRepository.findByIdAndTenantId(PRODUCT_ID, TENANT_ID))
                .willReturn(Optional.of(product));

        reservationService.handleOrderCancelled(event);

        // Stock should be restored
        verify(productRepository).saveAll(productListCaptor.capture());
        assertThat(productListCaptor.getValue().getFirst().getStockQuantity()).isEqualTo(10);

        // Reservation should be marked RELEASED
        verify(stockReservationRepository).saveAll(reservationListCaptor.capture());
        assertThat(reservationListCaptor.getValue().getFirst().getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void shouldSkipDuplicateCancellationEvent() {
        EventEnvelope event = orderCancelledEvent();

        doThrow(new DuplicateKeyException("duplicate"))
                .when(processedEventRepository).insert(any(com.pmfml.orderflow.inventoryservice.entities.ProcessedEvent.class));

        reservationService.handleOrderCancelled(event);

        verify(stockReservationRepository, never()).findByOrderIdAndTenantId(anyString(), anyString());
    }

    @Test
    void shouldHandleCancellationWithNoReservations() {
        EventEnvelope event = orderCancelledEvent();

        given(stockReservationRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(Collections.emptyList());

        reservationService.handleOrderCancelled(event);

        // Should not attempt to save anything
        verify(productRepository, never()).saveAll(any());
        verify(stockReservationRepository, never()).saveAll(any());
    }

    @Test
    void shouldSkipAlreadyReleasedReservations() {
        EventEnvelope event = orderCancelledEvent();
        StockReservation alreadyReleased = reservedReservation(PRODUCT_ID, 3);
        alreadyReleased.setStatus(ReservationStatus.RELEASED);

        given(stockReservationRepository.findByOrderIdAndTenantId(ORDER_ID, TENANT_ID))
                .willReturn(List.of(alreadyReleased));

        reservationService.handleOrderCancelled(event);

        // Should not look up the product since reservation is already released
        verify(productRepository, never()).findByIdAndTenantId(anyString(), anyString());
        // Should still save the (empty) lists
        verify(productRepository).saveAll(productListCaptor.capture());
        assertThat(productListCaptor.getValue()).isEmpty();
    }

    // ---------------------------------------------------------------
    // Test helpers
    // ---------------------------------------------------------------

    private EventEnvelope orderCreatedEvent(String productId, int quantity, double totalAmount) {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.ORDER_CREATED,
                TENANT_ID,
                Instant.now(),
                Map.of(
                        "orderId", ORDER_ID,
                        "totalAmount", totalAmount,
                        "items", List.of(Map.of(
                                "productId", productId,
                                "quantity", quantity
                        ))
                )
        );
    }

    private EventEnvelope orderCancelledEvent() {
        return new EventEnvelope(
                UUID.randomUUID(),
                EventTypes.ORDER_CANCELLED,
                TENANT_ID,
                Instant.now(),
                Map.of("orderId", ORDER_ID)
        );
    }

    private Product productWithStock(String id, int stock) {
        return Product.builder()
                .id(id)
                .tenantId(TENANT_ID)
                .sku("SKU-" + id)
                .price(BigDecimal.TEN)
                .stockQuantity(stock)
                .build();
    }

    private StockReservation reservedReservation(String productId, int quantity) {
        return StockReservation.builder()
                .orderId(ORDER_ID)
                .tenantId(TENANT_ID)
                .productId(productId)
                .quantity(quantity)
                .status(ReservationStatus.RESERVED)
                .createdAt(Instant.now())
                .build();
    }
}
