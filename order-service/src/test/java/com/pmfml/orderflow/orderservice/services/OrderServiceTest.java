package com.pmfml.orderflow.orderservice.services;

import com.pmfml.orderflow.common.events.EventTypes;
import com.pmfml.orderflow.orderservice.dtos.CreateOrderRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderItemRequest;
import com.pmfml.orderflow.orderservice.dtos.OrderResponse;
import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OutboxEvent;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import com.pmfml.orderflow.orderservice.grpc.InventoryClient;
import com.pmfml.orderflow.orderservice.repositories.OrderRepository;
import com.pmfml.orderflow.orderservice.repositories.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private InventoryClient inventoryClient;

    // Use the real mapper since it's just a simple POJO converter with no external dependencies
    private final OrderMapper orderMapper = new OrderMapper();

    /**
     * A real serializer, not a mock. Mocking it would let the test pass while
     * asserting nothing about the actual event payload, which is the contract
     * consumers depend on (docs/EVENTS.md).
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private OrderService orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                outboxEventRepository,
                inventoryClient,
                orderMapper,
                objectMapper
        );
    }

    @Test
    void shouldCreateOrderAndOutboxEvent() {
        // Given
        String tenantId = "tenant-123";
        UUID assignedId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("prod-1", 2),
                new OrderItemRequest("prod-2", 1)
        ));

        given(inventoryClient.fetchAvailableProduct("prod-1", 2, tenantId))
                .willReturn(new InventoryClient.ProductInfo("prod-1", "Laptop", new BigDecimal("1000.00")));
        given(inventoryClient.fetchAvailableProduct("prod-2", 1, tenantId))
                .willReturn(new InventoryClient.ProductInfo("prod-2", "Mouse", new BigDecimal("50.00")));

        // Mirror JPA semantics: save() returns the same managed instance, with an id
        // assigned. Returning a detached stub would hide the items from the payload.
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order toSave = invocation.getArgument(0);
            toSave.setId(assignedId);
            return toSave;
        });

        // When
        OrderResponse response = orderService.createOrder(request, tenantId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("2050.00"));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

        // Each line's own quantity must reach the stock check, otherwise the
        // Inventory Service cannot tell an order for 1 unit from one for 500.
        verify(inventoryClient).fetchAvailableProduct("prod-1", 2, tenantId);
        verify(inventoryClient).fetchAvailableProduct("prod-2", 1, tenantId);

        // Verify Order aggregate was built correctly
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getTenantId()).isEqualTo(tenantId);
        assertThat(capturedOrder.getTotalAmount()).isEqualTo(new BigDecimal("2050.00")); // (1000 * 2) + (50 * 1)
        assertThat(capturedOrder.getItems()).hasSize(2);
        assertThat(capturedOrder.getItems().get(0).getProductName()).isEqualTo("Laptop");

        // Verify Outbox Event metadata
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertThat(capturedEvent.getAggregateType()).isEqualTo("Order");
        assertThat(capturedEvent.getEventType()).isEqualTo(EventTypes.ORDER_CREATED);
        assertThat(capturedEvent.getAggregateId()).isEqualTo(assignedId);
        assertThat(capturedEvent.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWritePayloadMatchingTheDocumentedContract() {
        // Given
        String tenantId = "tenant-123";
        UUID assignedId = UUID.randomUUID();
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("prod-1", 2),
                new OrderItemRequest("prod-2", 1)
        ));

        given(inventoryClient.fetchAvailableProduct("prod-1", 2, tenantId))
                .willReturn(new InventoryClient.ProductInfo("prod-1", "Laptop", new BigDecimal("1000.00")));
        given(inventoryClient.fetchAvailableProduct("prod-2", 1, tenantId))
                .willReturn(new InventoryClient.ProductInfo("prod-2", "Mouse", new BigDecimal("50.00")));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
            Order toSave = invocation.getArgument(0);
            toSave.setId(assignedId);
            return toSave;
        });

        // When
        orderService.createOrder(request, tenantId);

        // Then — the stored payload holds only event-specific data. Envelope fields
        // (eventId, eventType, tenantId, occurredAt) are added by the poller.
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        Map<String, Object> payload =
                objectMapper.readValue(outboxEventCaptor.getValue().getPayload(), Map.class);

        assertThat(payload)
                .containsOnlyKeys("orderId", "status", "totalAmount", "items")
                .containsEntry("orderId", assignedId.toString())
                .containsEntry("status", OrderStatus.PENDING.name());
        assertThat(((Number) payload.get("totalAmount")).doubleValue()).isEqualTo(2050.00);

        // Line items are required so the Inventory Service can reserve stock
        // without calling back into this service.
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0))
                .containsEntry("productId", "prod-1")
                .containsEntry("quantity", 2);
        assertThat(((Number) items.get(0).get("unitPrice")).doubleValue()).isEqualTo(1000.00);
        assertThat(items.get(1))
                .containsEntry("productId", "prod-2")
                .containsEntry("quantity", 1);
    }

    @Test
    void shouldThrowExceptionWhenSerializationFails() {
        // Given — only this scenario needs a stubbed serializer, since a real one
        // cannot be made to fail on a well-formed payload.
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        OrderService serviceWithFailingMapper = new OrderService(
                orderRepository, outboxEventRepository, inventoryClient, orderMapper, failingMapper);

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("prod-1", 1)));

        given(inventoryClient.fetchAvailableProduct("prod-1", 1, "tenant-1"))
                .willReturn(new InventoryClient.ProductInfo("prod-1", "A", BigDecimal.TEN));

        Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .tenantId("tenant-1")
                .totalAmount(BigDecimal.TEN)
                .build();
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

        given(failingMapper.writeValueAsString(any())).willThrow(new JacksonException("Mock error") {
        });

        // When & Then
        assertThatThrownBy(() -> serviceWithFailingMapper.createOrder(request, "tenant-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize outbox event payload");

        // Order is "saved" in memory, but because the method throws a RuntimeException,
        // the @Transactional proxy will roll back the physical database transaction.
    }
}
