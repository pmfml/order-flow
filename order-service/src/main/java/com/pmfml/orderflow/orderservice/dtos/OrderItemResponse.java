package com.pmfml.orderflow.orderservice.dtos;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO for a single order line item.
 */
public record OrderItemResponse(
        UUID id,
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
