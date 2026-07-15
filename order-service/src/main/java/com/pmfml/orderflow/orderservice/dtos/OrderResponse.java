package com.pmfml.orderflow.orderservice.dtos;

import com.pmfml.orderflow.orderservice.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for the root Order aggregate.
 */
public record OrderResponse(
        UUID id,
        String tenantId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {
}
