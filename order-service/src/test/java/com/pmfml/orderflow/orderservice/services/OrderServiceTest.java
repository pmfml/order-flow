package com.pmfml.orderflow.orderservice.services;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private ObjectMapper objectMapper;

    // Use the real mapper since it's just a simple POJO converter with no external dependencies
    private final OrderMapper orderMapper = new OrderMapper();

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
    void shouldCreateOrderAndOutboxEvent() throws JacksonException {
        // Given
        String tenantId = "tenant-123";
        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("prod-1", 2),
                new OrderItemRequest("prod-2", 1)
        ));

        given(inventoryClient.fetchProduct("prod-1"))
                .willReturn(new InventoryClient.ProductInfo("prod-1", "Laptop", new BigDecimal("1000.00")));
        given(inventoryClient.fetchProduct("prod-2"))
                .willReturn(new InventoryClient.ProductInfo("prod-2", "Mouse", new BigDecimal("50.00")));

        Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("2050.00"))
                .build();
        
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);
        given(objectMapper.writeValueAsString(any())).willReturn("{\"mock\":\"json\"}");

        // When
        OrderResponse response = orderService.createOrder(request, tenantId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("2050.00"));
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

        // Verify Order aggregate was built correctly
        verify(orderRepository).save(orderCaptor.capture());
        Order capturedOrder = orderCaptor.getValue();
        assertThat(capturedOrder.getTenantId()).isEqualTo(tenantId);
        assertThat(capturedOrder.getTotalAmount()).isEqualTo(new BigDecimal("2050.00")); // (1000 * 2) + (50 * 1)
        assertThat(capturedOrder.getItems()).hasSize(2);
        assertThat(capturedOrder.getItems().get(0).getProductName()).isEqualTo("Laptop");

        // Verify Outbox Event was written
        verify(outboxEventRepository).save(outboxEventCaptor.capture());
        OutboxEvent capturedEvent = outboxEventCaptor.getValue();
        assertThat(capturedEvent.getAggregateType()).isEqualTo("Order");
        assertThat(capturedEvent.getEventType()).isEqualTo("orders.created");
        assertThat(capturedEvent.getAggregateId()).isEqualTo(savedOrder.getId());
        assertThat(capturedEvent.getPayload()).isEqualTo("{\"mock\":\"json\"}");
    }

    @Test
    void shouldThrowExceptionWhenSerializationFails() throws JacksonException {
        // Given
        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest("prod-1", 1)));
        
        given(inventoryClient.fetchProduct("prod-1"))
                .willReturn(new InventoryClient.ProductInfo("prod-1", "A", BigDecimal.TEN));
                
        Order savedOrder = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .tenantId("tenant-1")
                .totalAmount(BigDecimal.TEN)
                .build();
        given(orderRepository.save(any(Order.class))).willReturn(savedOrder);
        
        given(objectMapper.writeValueAsString(any())).willThrow(new JacksonException("Mock error") {});

        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request, "tenant-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize outbox event payload");
                
        // Order is "saved" in memory, but because the method throws a RuntimeException, 
        // the @Transactional proxy will roll back the physical database transaction.
    }
}
