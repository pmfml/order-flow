package com.pmfml.orderflow.orderservice.services;

import com.pmfml.orderflow.orderservice.dtos.OrderItemResponse;
import com.pmfml.orderflow.orderservice.dtos.OrderResponse;
import com.pmfml.orderflow.orderservice.entities.Order;
import com.pmfml.orderflow.orderservice.entities.OrderItem;
import com.pmfml.orderflow.orderservice.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper orderMapper = new OrderMapper();

    @Test
    void shouldMapOrderToOrderResponse() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.now();

        Order order = Order.builder()
                .id(orderId)
                .tenantId("tenant-x")
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("150.00"))
                .createdAt(now)
                .updatedAt(now)
                .build();

        OrderItem item = OrderItem.builder()
                .id(itemId)
                .productId("prod-1")
                .productName("Test Product")
                .quantity(2)
                .unitPrice(new BigDecimal("75.00"))
                .build();

        order.addItem(item);

        // Act
        OrderResponse response = orderMapper.toResponse(order);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.tenantId()).isEqualTo("tenant-x");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("150.00"));
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.updatedAt()).isEqualTo(now);

        assertThat(response.items()).hasSize(1);
        OrderItemResponse itemResponse = response.items().getFirst();
        assertThat(itemResponse.id()).isEqualTo(itemId);
        assertThat(itemResponse.productId()).isEqualTo("prod-1");
        assertThat(itemResponse.productName()).isEqualTo("Test Product");
        assertThat(itemResponse.quantity()).isEqualTo(2);
        assertThat(itemResponse.unitPrice()).isEqualTo(new BigDecimal("75.00"));
    }
}
